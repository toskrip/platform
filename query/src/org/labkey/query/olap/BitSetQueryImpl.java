/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.query.olap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.query.controllers.OlapController;
import org.labkey.query.olap.metadata.CachedCube;
import org.labkey.query.olap.rolap.RolapCubeDef;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.OlapException;
import org.olap4j.OlapStatement;
import org.olap4j.Position;
import org.olap4j.mdx.ParseTreeNode;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Measure;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.MetadataElement;
import org.olap4j.metadata.NamedList;
import org.olap4j.metadata.Property;
import org.springframework.validation.BindException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.labkey.query.olap.QubeQuery.OP;

/**
 * Created by matthew on 3/13/14.
 *
 */
public class BitSetQueryImpl
{
    // mondrian compatibility, ignore NULL associations if member is associated with any non-null members
    // e.g. Will will ignore the fact that Subject has a row with BMI IS NULL, if there is a row where BMI IS NOT NULL
    // NOTE: this is for validation against Mondrian only, the current implementation is NOT robust!
    final boolean mondrianCompatibleNullHandling = false;

    static Logger _log = Logger.getLogger(BitSetQueryImpl.class);
    final static User serviceUser = new LimitedUser(User.guest, new int[0], Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);
    static
    {
        serviceUser.setPrincipalType(PrincipalType.SERVICE);
        serviceUser.setDisplayName("Internal JDBC Service User");
        serviceUser.setEmail("internaljdbc@labkey.org");
    }

    final Container container;
    final Cube cube;
    final CaseInsensitiveHashMap<Level> levelMap = new CaseInsensitiveHashMap<>();
    final QubeQuery qq;
    final RolapCubeDef rolap;
    final BindException errors;
    MeasureDef measure;
    OlapConnection connection;
    final String cachePrefix;
    SqlDialect dialect = null;
    final User user;

    CubeDataSourceHelper _cdsh = new CubeDataSourceHelper();
    SqlDataSourceHelper _sdsh = new SqlDataSourceHelper();
    IDataSourceHelper _dataSourceHelper = OlapController.strategy == OlapController.ImplStrategy.rolapYourOwn ? _sdsh : _cdsh;


    MemberSet containerMembers = null;  // null == all

    public BitSetQueryImpl(Container c, OlapSchemaDescriptor sd, Cube cube, OlapConnection connection, QubeQuery qq,
           BindException errors, boolean useSQL /* for testing */
           ) throws SQLException, IOException
    {
        this.user = serviceUser;
        this.container = c;
        this.connection = connection;
        this.qq = qq;
        this.cube = qq.getCube();
        this.errors = errors;

//        this._dataSourceHelper = useSQL ? _sdsh : _cdsh;
//        this._dataSourceHelper = new DoubleDownHelper();
//        this._dataSourceHelper = _sdsh;

        RolapCubeDef r = null;
        List<RolapCubeDef> defs = sd.getRolapCubeDefinitions();
        for (RolapCubeDef d : defs)
        {
            if (d.getName().equals(cube.getName()))
                r = d;
        }
        rolap = r;
        if (null == rolap)
            throw new IllegalStateException("Rolap definition not found: " + cube.getName());
        String schemaName = rolap.getSchemaName();
        QuerySchema s = DefaultSchema.get(user, c).getSchema(schemaName);
        if (null != s)
            dialect = s.getDbSchema().getSqlDialect();

        String cubeId = cube.getUniqueName() +
                ((cube instanceof CachedCube)?"@" + ((CachedCube)cube).getLongHashCode() : "");
        this.cachePrefix = "" + c.getRowId() + "/" + sd.getId() + "/" + cubeId + "/";

        initCube();
        initDistinctMeasure();
    }


    public BitSetQueryImpl setContainerFilter(Collection<String> containerFilter) throws OlapException
    {
        // inspect the masure hierarchy to see if it has a container level
        Hierarchy h = qq.countDistinctLevel.getHierarchy();
        Level lContainer = null;
        for (int i=1 ; i<qq.countDistinctLevel.getDepth() ; i++)
        {
            Level l = h.getLevels().get(i);
            if (l.getName().equalsIgnoreCase("container"))
                lContainer = l;
        }

        CaseInsensitiveHashSet ids = new CaseInsensitiveHashSet();
        ids.addAll(containerFilter);
        MemberSet s = new MemberSet();
        for (Member m : lContainer.getMembers())
            if (ids.contains(m.getName()))
                s.add(m);

        this.containerMembers = s;
        return this;
    }


    void initCube() throws OlapException
    {
        CPUTimer t = new CPUTimer("initCube");
        assert t.start();
        for (Hierarchy h : cube.getHierarchies())
        {
            for (Level l : h.getLevels())
            {
                levelMap.put(l.getUniqueName(),l);
                if (!(cube instanceof CachedCube))
                {
                    // Member ordinals may not be set, which is really annoying
                    List<Member> members = l.getMembers();
                    int count = members.size();
                    for (int i = 0; i < count; i++)
                    {
                        Member m = members.get(i);
                        m.setProperty(Property.StandardMemberProperty.MEMBER_ORDINAL, String.valueOf(i));
                        assert m.getOrdinal() == i;
                    }
                }
            }
        }
        assert t.stop();
    }


    void initDistinctMeasure()
    {
        if (null == qq.countDistinctMember && null == qq.countDistinctLevel)
        {
            for (Measure m : cube.getMeasures())
            {
                if (m.getName().endsWith("Count") && !m.getName().equals("RowCount"))
                    qq.countDistinctMember = m;
            }
        }

        if (null != qq.countDistinctMember && null == qq.countDistinctLevel)
        {
            String name = qq.countDistinctMember.getName();
            if (name.endsWith("Count"))
                name = name.substring(0,name.length()-5);
            Hierarchy h = cube.getHierarchies().get(name);
            if (null == h)
                h = cube.getHierarchies().get("Participant");
            if (null == h)
                h = cube.getHierarchies().get("Subject");
            if (null == h)
                h = cube.getHierarchies().get("Patient");
            if (h != null)
                qq.countDistinctLevel = h.getLevels().get(h.getLevels().size()-1);
        }

        if (null == qq.countDistinctLevel)
            throw new IllegalArgumentException("No count distinct measure definition found");

        if (null == qq.countDistinctMember)
        {
            qq.countDistinctMember = new _CountDistinctMeasure(qq.countDistinctLevel);
        }

        // NOTE we don't actually make use of countDistinctMember after this point, except in the cellset
        this.measure = new CountDistinctMeasureDef(qq.countDistinctMember, qq.countDistinctLevel);
    }



    abstract class Result
    {
        // return level if all members are in the same level, null otherwise
        @Nullable abstract Level getLevel();
        // return heirarchy if all members are in the same heirarchy, null otherwise
        @Nullable abstract Hierarchy getHierarchy();
        abstract void toMdxSet(StringBuilder sb);
        @NotNull abstract Collection<Member> getCollection() throws OlapException;

        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            toMdxSet(sb);
            return sb.toString();
        }
    }


    class MemberSetResult extends Result
    {
        MemberSetResult(Set<Member> members)
        {
            this.level = null;
            this.hierarchy = null;
            if (members instanceof MemberSet)
            {
                this.members = (MemberSet) members;
                this.member = null;
            }
//            else if (members.size() == 1)
//            {
//                this.members = null;
//                this.member = members.toArray(new Member[1])[0];
//            }
            else
            {
                this.member = null;
                this.members = new MemberSet(members);
            }
        }

        MemberSetResult(Level level)
        {
            this.members = null;
            this.level = level;
            this.hierarchy = null;
            this.member = null;
        }

        MemberSetResult(Hierarchy h)
        {
            this.members = null;
            this.level = null;
            this.hierarchy = h;
            this.member = null;
        }

        void toMdxSet(StringBuilder sb)
        {
            if (null != member)
            {
                sb.append("{");
                sb.append(member.getUniqueName());
                sb.append("}\n");
            }
            else if (null != level)
            {
                sb.append(level.getUniqueName()).append(".members");
            }
            else if (null != hierarchy)
            {
                sb.append(hierarchy.getUniqueName()).append(".members");
            }
            else
            {
                sb.append("{");
                String comma = "";
                for (Member m : members)
                {
                    sb.append(comma);
                    sb.append(m.getUniqueName());
                    comma = ",";
                }
                sb.append("}\n");
            }
        }

        Level getLevel()
        {
            if (null != member)
                return member.getLevel();
            if (null != level)
                return level;
            if (null != hierarchy)
                return null;
            if (null != members)
                return members.getLevel();
            return null;
        }

        Hierarchy getHierarchy()
        {
            if (null != member)
                return member.getHierarchy();
            if (null != level)
                return level.getHierarchy();
            if (null != hierarchy)
                return hierarchy;
            if (null != members)
                return members.getHierarchy();
            return null;
        }


        @NotNull
        Collection<Member> getCollection() throws OlapException
        {
            if (null != member)
            {
                return Collections.singletonList(member);
            }
            else if (null != level)
            {
                return level.getMembers();
            }
            else if (null != hierarchy)
            {
                List<Member> ret = new ArrayList<>();
                for (Member r : hierarchy.getRootMembers())
                    addMemberAndChildren(r,ret);
                return ret;
            }
            else
            {
                return members;
            }
        }


        final Level level;              // all members of a level
        final Hierarchy hierarchy;      // all members of a hierarchy
        final Member member;            // special case, one member (used in filters)
        final MemberSet members;        // explicit list of members

        @Override
        public String toString()
        {
            Hierarchy h = null;
            Level l = null;
            int count = -1;
            if (null != level)
            {
                h = level.getHierarchy();
                l = level;
                try {count = level.getMembers().size();} catch (OlapException e){/* */}
            }
            else if (null != hierarchy)
            {
                h = hierarchy;
            }
            else if (null != members)
            {
                h = members.getHierarchy();
                l = members.getLevel();
                count = members.size();
            }
            return "MemberSet " +
                    (null != l ? l.getUniqueName() : (null != h ? h.getUniqueName() : "")) +
                    (count == -1 ? "" : " " + String.valueOf(count));
        }
    }


    class UnionResult extends Result
    {
        List<Result> results = new ArrayList<>();

        UnionResult(Collection<Result> results)
        {
            this.results.addAll(results);
        }

        @Nullable
        @Override
        Level getLevel()
        {
            Level l = null;
            for (Result r : results)
            {
                Level resultLevel = r.getLevel();
                if (null == resultLevel)
                    return null;
                if (null == l)
                    l = resultLevel;
                else if (!same(l, resultLevel))
                    return null;
            }
            return l;
        }

        @Nullable
        @Override
        Hierarchy getHierarchy()
        {
            Hierarchy h = null;
            for (Result r : results)
            {
                Hierarchy resultHierarchy = r.getHierarchy();
                if (null == resultHierarchy)
                    return null;
                if (null == h)
                    h = resultHierarchy;
                else if (!same(h, resultHierarchy))
                    return null;
            }
            return h;
        }

        @NotNull
        @Override
        Collection<Member> getCollection() throws OlapException
        {
            LinkedHashSet<Member> ret = new LinkedHashSet<>();
            for (Result r : results)
                ret.addAll(r.getCollection());
            return ret;
        }

        @Override
        void toMdxSet(StringBuilder sb)
        {
            if (results.size() == 1)
            {
                results.get(0).toMdxSet(sb);
                return;
            }
            sb.append(" UNION(");
            String comma = "";
            for (Result r : results)
            {
                sb.append(comma);
                r.toMdxSet(sb);
                comma = ", ";
            }
            sb.append(")\n");
        }
    }


    class CrossResult extends Result
    {
        List<Result> results = new ArrayList<>();

        CrossResult()
        {}

        CrossResult(Collection<Result> results)
        {
            this.results.addAll(results);
        }

        @Nullable
        @Override
        Level getLevel()
        {
            return null;
        }

        @Nullable
        @Override
        Hierarchy getHierarchy()
        {
            return null;
        }

        @NotNull
        @Override
        Collection<Member> getCollection() throws OlapException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        void toMdxSet(StringBuilder sb)
        {
            if (results.size() == 1)
            {
                results.get(0).toMdxSet(sb);
            }
            else if (results.size() == 2)
            {
                sb.append("CROSSJOIN(");
                results.get(0).toMdxSet(sb);
                sb.append(",");
                results.get(1).toMdxSet(sb);
                sb.append(")");
            }
            else
            {
                throw new UnsupportedOperationException("NYI");
            }
        }

        @Override
        public String toString()
        {
            if (results.size() == 1)
                return results.get(0).toString();

            StringBuilder sb = new StringBuilder();
            String x = "";
            for (Result r : results)
            {
                sb.append(x);
                sb.append(r.toString());
                x = " CROSSJOIN ";
            }
            return sb.toString();
        }
    }



    void addMemberAndChildren(Member m, Collection<Member> list) throws OlapException
    {
        list.add(m);
        for (Member c : m.getChildMembers())
            addMemberAndChildren(c, list);
    }


    void addMemberAndChildrenInLevel(Member m, Level filter, Collection<Member> list) throws OlapException
    {
        if (same(m.getLevel(), filter))
        {
            list.add(m);
            return;
        }
        for (Member c : m.getChildMembers())
            addMemberAndChildrenInLevel(c, filter, list);
    }



//    // there are different types of data that can be manipulated by this "query language" to use
//    // the term loosely.
//    enum Type
//    {
//        countOfMembers,     // integer
//        member,             // individual categorical value
//        setOfMembers,       // set of categorical values
//        tupleMemberCount,   // (member, count)
//        tupleMemberMembers,  // (member, {members})
//        setOfTuples,         // {(member,count),(member,count)}
//        crossSet,             // {{member}}, 0-1 memberset per hierarchy (no duplicates)
//        union             // {{member}}, 0-1 memberset per hierarchy (no duplicates)
//    }
//
//
    enum Operator
    {
        Count,
        Intersect,
        Union,
        Filter,
        FilterBy
    }


    enum MeasureOperator
    {
        count,
        countDistinct,
        sum
    }


    public static abstract class MeasureDef
    {
        final MeasureOperator op;
        Member measureMember;
        MeasureDef(Member member, MeasureOperator op)
        {
            this.op = op;
            this.measureMember = member;
        }
    }


    static class CountDistinctMeasureDef extends MeasureDef
    {
        final Level level;

        CountDistinctMeasureDef(Member member, Level l)
        {
            super(member, MeasureOperator.countDistinct);
            level = l;
        }

        @Override
        public String toString()
        {
            return  op.name() + "(" + level.getUniqueName() + ")";
        }
    }


    Result processMembers(QubeQuery.QubeMembersExpr expr) throws SQLException
    {
        if (null != expr.membersSet)
            return new MemberSetResult(expr.membersSet);

        MemberSetResult outer = null;

        if (null != expr.hierarchy && null == expr.level)
        {
            if (expr.childrenMember)
            {
                expr.level = expr.hierarchy.getLevels().get(1);
                expr.hierarchy = null;
            }
        }

        if (null != expr.level)
        {
            outer = new MemberSetResult(expr.level);
        }
        else if (null != expr.hierarchy)
        {
            outer = new MemberSetResult(expr.hierarchy);
        }

        if (null != outer && null != expr.membersQuery)
        {
            Result subquery = processExpr(expr.membersQuery);
            MemberSet filtered;
            if (subquery instanceof MemberSetResult)
                filtered = _dataSourceHelper.membersQuery(outer, (MemberSetResult)subquery);
            else if (subquery instanceof CrossResult)
                filtered = _dataSourceHelper.membersQuery(outer, (CrossResult)subquery);
            else
                throw new IllegalStateException("unsupported query, did not expect " + subquery.getClass().getName());
            return new MemberSetResult(filtered);
        }

        if (null != outer)
            return outer;

        throw new IllegalStateException();
    }


    Result processExpr(QubeQuery.QubeExpr expr) throws SQLException
    {
        while (null != expr.arguments && expr.arguments.size() == 1 && expr.op != QubeQuery.OP.MEMBERS)
            expr = expr.arguments.get(0);

        if (expr.op == QubeQuery.OP.MEMBERS)
        {
            return processMembers((QubeQuery.QubeMembersExpr)expr);
        }

        if (null == expr.arguments || 0 == expr.arguments.size())
            throw new IllegalArgumentException("No arguments provided");

        List<Result> results = new ArrayList<>(expr.arguments.size());
        for (QubeQuery.QubeExpr in : expr.arguments)
        {
            results.add(processExpr(in));
        }

        switch (expr.op)
        {
        case CROSSJOIN:
        case XINTERSECT:
        {
            return crossjoin(expr.op, results);
        }
        case INTERSECT:
        {
            return intersect(expr.op, results);
        }
        case UNION:
        {
            return union(expr.op, results);
        }
        default:
        case MEMBERS:
        {
            throw new IllegalStateException();
        }
        }
    }


    CellSet evaluate(Result rowsExpr, Result colsExpr, Result filterExpr) throws SQLException
    {
        if (null == rowsExpr && null == colsExpr)
            throw new IllegalArgumentException();

        Level measureLevel = ((CountDistinctMeasureDef)measure).level;

        // STEP 1: create filter set (measure members selected by filter)
        MemberSet filterSet = filter(measureLevel, filterExpr, containerMembers);

        if (_log.isDebugEnabled())
        {
            StringBuilder sb = new StringBuilder();
            sb.append("evaluate()");
            sb.append("\n\tmeasure   ").append(measure.toString());
            if (null != rowsExpr)
            {
                sb.append("\n\tonrows    ").append(rowsExpr.toString());
            }
            if (null != colsExpr)
            {
                sb.append("\n\toncolumns ").append(colsExpr.toString());
            }
            if (null != filterExpr)
            {
                sb.append("\n\tfilter    ").append(filterExpr.toString());
            }
            if (null != filterSet)
            {
                sb.append("\n\teval      ").append(filterSet.toString());
            }
            logDebug(sb.toString());
        }

        ArrayList<Number> measureValues = new ArrayList<>();
        int countAllMeasureMembers = measureLevel.getMembers().size();
        int countFilterSet = null == filterSet ? -1 : filterSet.size();
        // if the filter returns all members, it's not much of a filter is it?
        if (countFilterSet == countAllMeasureMembers)
        {
            filterSet = null;
            countFilterSet = -1;
        }

        if (null != colsExpr)
            _dataSourceHelper.populateCache(measureLevel, colsExpr);
        if (null != rowsExpr)
            _dataSourceHelper.populateCache(measureLevel, rowsExpr);

        Collection<Member> rowMembers = null==rowsExpr ? null : rowsExpr.getCollection();
        Collection<Member> colMembers = null==colsExpr ? null : colsExpr.getCollection();

        // ONE-AXIS
        if (null == colsExpr || null == rowsExpr)
        {
            Collection<Member> members = null==rowMembers ? colMembers : rowMembers;
            Collection<Member> notEmptyMembers = !qq.showEmpty ? new ArrayList<Member>() : null;
            for (Member m : members)
            {
                int count;
                if (0 == countFilterSet)
                {
                    count = 0;
                }
                else
                {
                    MemberSet memberSet = _dataSourceHelper.membersQuery(measureLevel, m);
                    if (null == filterSet)
                        count = memberSet.size();
                    else
                        count = MemberSet.countIntersect(memberSet, filterSet);
                }
                if (qq.showEmpty)
                    measureValues.add(0==count?null:count);
                else
                {
                    if (0==count)
                        continue;
                    measureValues.add(count);
                    notEmptyMembers.add(m);
                }
            }
            if (!qq.showEmpty)
            {
                if (null != rowMembers)
                    rowMembers = notEmptyMembers;
                else
                    colMembers = notEmptyMembers;
            }
        }
        // TWO-AXIS
        else
        {
            // TODO handle showEmpty==false for two axis query
            HashMap<String,MemberSet> quickCache = new HashMap<>();
            for (Member rowMember : rowsExpr.getCollection())
            {
                MemberSet rowMemberSet = null;

                for (Member colMember : colsExpr.getCollection())
                {
                    int count;
                    if (0 == countFilterSet)
                    {
                        count = 0;
                    }
                    else
                    {
                        if (null == rowMemberSet)
                            rowMemberSet = _dataSourceHelper.membersQuery(measureLevel, rowMember);

                        MemberSet colMemberSet = quickCache.get(colMember.getUniqueName());
                        if (null == colMemberSet)
                        {
                            colMemberSet = _dataSourceHelper.membersQuery(measureLevel, colMember);
                            quickCache.put(colMember.getUniqueName(),colMemberSet);
                        }
                        if (null == filterSet)
                            count = MemberSet.countIntersect(rowMemberSet, colMemberSet);
                        else
                            count = MemberSet.countIntersect(rowMemberSet, colMemberSet, filterSet);
                    }
                    measureValues.add(count);
                }
            }
        }

        return new _CellSet(measureValues, colMembers, rowMembers);
    }


    MemberSet filter(Level measureLevel, Result filterAxisResult, MemberSet containerMembers) throws SQLException
    {
        List<Result> list = new ArrayList<>();

        if (filterAxisResult instanceof CrossResult)
        {
            list.addAll(((CrossResult)filterAxisResult).results);
        }
        else if (filterAxisResult instanceof MemberSetResult)
        {
            list.add(filterAxisResult);
        }
        else if (null != filterAxisResult)
        {
            throw new IllegalArgumentException();
        }

        if (null != containerMembers)
        {
            // this doesn't work because we're in the same hierarchy, we can just iterate over the children
            //MemberSetResult r = _dataSourceHelper.membersQuery(new MemberSetResult(measureLevel), new MemberSetResult(containerMembers));
            int size = containerMembers.size();
            Level level = containerMembers.getLevel();
            if (0 < size && null != level && size == level.getMembers().size())
            {
                // not much of a filter... skip it
            }
            else
            {
                MemberSet set = new MemberSet();
                for (Member m : containerMembers)
                {
                    List<? extends Member> children = (m instanceof CachedCube._Member) ? ((CachedCube._Member)m).getChildMembersArray() : m.getChildMembers();
                    for (Member c : children)
                    {
                        set.add(c);
                        assert same(c.getLevel(), measureLevel);
                    }
                }
                list.add(new MemberSetResult(set));
            }
        }

        if (list.isEmpty())
            return null;   // unfiltered

        MemberSet filteredSet = null;
        for (Result result : list)
        {
            Level resultLevel = result.getLevel();
            Hierarchy resultHierarchy = result.getHierarchy();

            /* NOTE: some CDS queries filter on the subject hierarchy instead of the subject level
             * for backward compatibility, unwind that here
             */
            if (result instanceof MemberSetResult && null == resultLevel && null != resultHierarchy &&
                    same(resultHierarchy, measureLevel.getHierarchy()))
            {
                // extract only the measure level (e.g. [Subject].[Subject]
                if (((MemberSetResult)result).hierarchy != null)
                {
                    result = new MemberSetResult(measureLevel);
                }
                else if (null != ((MemberSetResult)result).members)
                {
                    result = new MemberSetResult(((MemberSetResult)result).members.onlyFor(measureLevel));
                }
                resultLevel = measureLevel;
            }

            Result intersectSet;
            if (null != resultLevel && same(measureLevel,resultLevel))
            {
                intersectSet = result;
            }
            else
            {
                // because we only support COUNT DISTINCT, we can treat this filter like
                // NON EMPTY distinctLevel.members WHERE <filter>,
                // for SUM() or COUNT() this wouldn't work
                MemberSet set = _dataSourceHelper.membersQuery(new MemberSetResult(measureLevel), (MemberSetResult)result);
                intersectSet = new MemberSetResult(set);
            }
            if (null == filteredSet)
                filteredSet = new MemberSet(intersectSet.getCollection());
            else
                filteredSet.retainAll(intersectSet.getCollection());
        }
        return filteredSet;
    }


    /**
     * We don't actually perform the cross-join, we just collect the results by hierarchy.
     *
     * XINTERSECT is a made-up operator, that is a useful default for filters.  It simply
     * combines sets over the same level by doing an intersection.  NOTE: it could be smarter
     * about combining results within the same _hierarchy_ as well.  However, the main use case is
     * for combinding participant sets.
     */
    Result crossjoin(OP op, Collection<Result> results) throws OlapException
    {
        CrossResult cr = new CrossResult();
        Map<String,MemberSetResult> map = new LinkedHashMap<>();

        for (Result r : results)
        {
            Level l = r.getLevel();

            if (null == l || op == OP.CROSSJOIN)
            {
                cr.results.add(r);
            }
            else if (!map.containsKey(l.getUniqueName()))
            {
                map.put(l.getUniqueName(), (MemberSetResult)r);
            }
            else
            {
                Collection<Member> a = map.get(l.getUniqueName()).getCollection();
                Collection<Member> b = r.getCollection();
                MemberSet set = MemberSet.intersect(a,b);
                map.put(l.getUniqueName(), new MemberSetResult(set));
            }
        }
        cr.results.addAll(map.values());
        return cr;
    }


    Result intersect(OP op, Collection<Result> results) throws OlapException
    {
        if (results.size() == 0)
            throw new IllegalArgumentException();
        if (results.size() == 1)
            return results.iterator().next();

        List<Collection<Member>> sets = new ArrayList<>(results.size());
        for (Result r : results)
        {
            if (!(r instanceof MemberSetResult))
                throw new IllegalArgumentException();
            sets.add(((MemberSetResult)r).getCollection());
        }
        MemberSet s = MemberSet.intersect(sets);
        return new MemberSetResult(s);
    }



    Result union(OP op, Collection<Result> results) throws OlapException
    {
        if (results.size() == 0)
            throw new IllegalArgumentException();
        if (results.size() == 1)
            return results.iterator().next();

        // if all members are part of the same hierarchy just return a simple MemberSetResult
        // if there is more than one hierarchy involved, return a UnionResult
        Set<String> hierarchies = new HashSet<>();
        for (Result r : results)
        {
            if (!(r instanceof MemberSetResult))
                throw new IllegalArgumentException();
            Hierarchy h = ((MemberSetResult)r).getHierarchy();
            String name = null==h ? "NULL" : h.getUniqueName();
            hierarchies.add(name);
        }

        if (1 == hierarchies.size())
        {
            List<Collection<Member>> sets = new ArrayList<>(results.size());
            for (Result r : results)
            {
                if (!(r instanceof MemberSetResult))
                    throw new IllegalArgumentException();
                sets.add(((MemberSetResult) r).getCollection());
            }
            MemberSet s = MemberSet.union(sets);
            return new MemberSetResult(s);
        }

        // more than one hierarchy
        return new UnionResult(results);
    }




    //
    // special optimized subset of query/mdx functionality
    // this method supports:
    //   * simple list/set of members on rows (no hierarchy/cross-products)
    //   * simple list/set of members on columns
    //   * one count(distinct) measure at intersections
    //

    public CellSet executeQuery() throws BindException,SQLException
    {

        Result filterExpr=null, rowsExpr=null, columnsExpr=null;

        if (null != qq.onColumns)
            columnsExpr = processExpr(qq.onColumns);
        if (null != qq.onRows)
            rowsExpr = processExpr(qq.onRows);
        if (null != qq.filters && qq.filters.arguments.size() > 0)
            filterExpr = processExpr(qq.filters);

        CellSet ret;
        ret = evaluate(rowsExpr, columnsExpr, filterExpr);
        return ret;
    }


    static StringKeyCache<MemberSet> _resultsCache = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, TimeUnit.HOURS.toMillis(1), "olap - count distinct queries");

    MemberSet resultsCacheGet(String query)
    {
        String key = cachePrefix + query;
        MemberSet m = _resultsCache.get(key);
        if (null == m)
            return null;
        return m.attach(levelMap);
    }

    void resultsCachePut(String query, MemberSet m)
    {
        String key = cachePrefix + query;
        _resultsCache.put(key, m.detach());
        if (_log.isDebugEnabled())
        {
            long size = m.getMemorySizeInBytes();
            logDebug("cached object size: " + size);
        }
    }



    OlapConnection getOlapConnection()
    {
        return connection;
    }


    interface IDataSourceHelper
    {
            MemberSet membersQuery(MemberSetResult outer, MemberSetResult sub) throws SQLException;

            MemberSet membersQuery(Level outer, Member sub) throws SQLException;

            MemberSet membersQuery(MemberSetResult outer, CrossResult sub) throws SQLException;

            void populateCache(Level outerLevel, Result inner) throws SQLException;
   }


    // return sets/counts from the cube,
    class CubeDataSourceHelper implements IDataSourceHelper
    {
        CellSet execute(String query)
        {
            OlapStatement stmt = null;
            CellSet cs = null;
            try
            {
                OlapConnection conn = getOlapConnection();
                stmt = conn.createStatement();
                logDebug("\nSTART executeOlapQuery: --------------------------    --------------------------    --------------------------\n" + query);
                long ms = System.currentTimeMillis();
                cs = stmt.executeOlapQuery(query);
                long d = System.currentTimeMillis() - ms;
                QueryProfiler.getInstance().track(null, "-- MDX\n" + query, null, d, null, true);
                logDebug("\nEND executeOlapQuery: " + DateUtil.formatDuration(d) + " --------------------------    --------------------------    --------------------------\n");
                return cs;
            }
            catch (RuntimeException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
            finally
            {
                ResultSetUtil.close(cs);
                ResultSetUtil.close(stmt);
            }
        }


        String queryCrossjoin(Result from, Result to)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY CROSSJOIN(");
            from.toMdxSet(sb);
            sb.append(",");
            to.toMdxSet(sb);
            sb.append(") ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName());
            return sb.toString();
        }


        String queryCrossjoin(Level from, Result to)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY CROSSJOIN(");
            sb.append(from.getUniqueName()).append(".members");
            sb.append(",");
            to.toMdxSet(sb);
            sb.append(") ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName());
            return sb.toString();
        }


        String queryCrossjoin(Level from, Level to)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY CROSSJOIN(");
            sb.append(from.getUniqueName()).append(".members");
            sb.append(",");
            sb.append(to.getUniqueName()).append(".members");
            sb.append(") ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName());
            return sb.toString();
        }


        String queryIsNotEmpty(Level from, Member m)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY CROSSJOIN(");
            sb.append(from.getUniqueName()).append(".members");
            sb.append(",");
            sb.append(m.getUniqueName());
            sb.append(") ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName());
            return sb.toString();
        }


        String queryWhere(MemberSetResult from, Result where)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY ");
            from.toMdxSet(sb);
            sb.append(" ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName()).append("\n");
            sb.append("WHERE ");
            where.toMdxSet(sb);

            return sb.toString();
        }


        /**
         * return a set of members in <MemberSetResult=outer> that intersect (non empty cross join) with <MemberSetResult=sub>.
         * Note that this basically gives OR or UNION semantics w/respect to the sub members.
         */
        public MemberSet membersQuery(MemberSetResult outer, MemberSetResult sub) throws SQLException
        {
            MemberSet set;

            if (null != outer.level && sub instanceof MemberSetResult && null != ((MemberSetResult)sub).members && ((MemberSetResult)sub).members.size() == 1)
            {
                Iterator<Member> it = ((MemberSetResult)sub).members.iterator();
                it.hasNext();
                set = membersQuery(outer.level, it.next());
            }
            else
            {
                String query = queryCrossjoin(outer,sub);

                set = resultsCacheGet(query);
                if (null == set)
                {
                    if (null != outer.level)
                    {
                        populateCache(outer.getLevel(), sub);
                        set = new MemberSet();
                        for (Member m : sub.getCollection())
                        {
                            MemberSet inner = membersQuery(outer.level, m);
                            set.addAll(inner);
                        }
                        if (query.length() < 2000)
                            resultsCachePut(query, set);
                    }
                    else
                    {
                        // non-optimized case
                        try (CellSet cs = execute(query))
                        {
                            set = new MemberSet();
                            List<Position> rowPositions = cs.getAxes().get(1).getPositions();
                            for (int row=0 ; row<rowPositions.size() ; row++)
                            {
                                Position p = rowPositions.get(row);
                                Member m = p.getMembers().get(0);
                                double value = cs.getCell(row).getDoubleValue();
                                if (0 != value)
                                    set.add(getCubeMember(m));
                            }
                        }
                        resultsCachePut(query, set);
                    }
                }
            }

            return set;
        }


        /**
         * return a set of members in <Level=outer> that intersect (non empty cross join) with <Member=sub>
         */
        public MemberSet membersQuery(Level outer, Member sub) throws SQLException
        {
            if (same(sub.getHierarchy(), outer.getHierarchy()))
            {
                MemberSet s = new MemberSet();
                addMemberAndChildrenInLevel(sub, outer, s);
                return s;
            }

            String query = queryIsNotEmpty(outer, sub);
            MemberSet s = resultsCacheGet(query);
            if (null != s)
                return s;

            try (CellSet cs = execute(query))
            {
                MemberSet set = new MemberSet();
                List<Position> rowPositions = cs.getAxes().get(1).getPositions();
                for (int row=0 ; row<rowPositions.size() ; row++)
                {
                    Position p = rowPositions.get(row);
                    Member m = p.getMembers().get(0);
                    double value = cs.getCell(row).getDoubleValue();
                    if (0.0 != value)
                        set.add(getCubeMember(m));
                }
                resultsCachePut(query, set);
                return set;
            }
        }


        public MemberSet membersQuery(MemberSetResult outer, CrossResult sub) throws SQLException
        {
            String query = queryWhere(outer, sub);
            MemberSet s = resultsCacheGet(query);
            if (null != s)
                return s;

            try (CellSet cs = execute(query))
            {
                MemberSet set = new MemberSet();
                List<Position> rowPositions = cs.getAxes().get(1).getPositions();
                for (int row=0 ; row<rowPositions.size() ; row++)
                {
                    Position p = rowPositions.get(row);
                    Member m = p.getMembers().get(0);
                    double value = cs.getCell(row).getDoubleValue();
                    if (0.0 != value)
                        set.add(getCubeMember(m));
                }
                resultsCachePut(query, set);
                return set;
            }
        }



        public void populateCache(Level outerLevel, Result inner) throws SQLException
        {
            // MDX doesn't allow union across different hierarchies, but I do, so here I iterate over
            // the union components
            if (inner instanceof UnionResult)
            {
                for (Result r : ((UnionResult) inner).results)
                    populateCache(outerLevel, r);
                return;
            }


            boolean cacheMiss = false;

            // We cache by individual members, but we don't necessaryily want to load the cache one set at a time
            // we could find just the members that are not yet cached, but I'm just going to check if ANY are missing
            for (Member sub : inner.getCollection())
            {
                if (same(sub.getHierarchy(), outerLevel.getHierarchy()))
                    continue;
                String query = queryIsNotEmpty(outerLevel, sub);
                MemberSet s = resultsCacheGet(query);
                if (null == s)
                {
                    cacheMiss = true;
                    break;
                }
            }
            if (!cacheMiss)
                return;

            CaseInsensitiveHashMap<MemberSet> sets = new CaseInsensitiveHashMap<>();
            Collection<Member> innerCollection = inner.getCollection();
            for (Member sub : innerCollection)
                sets.put(sub.getUniqueName(), new MemberSet());

            boolean isLongList = false;
            if (inner instanceof MemberSetResult)
            {
                MemberSetResult msr = (MemberSetResult)inner;
                if (msr.members != null && msr.members.size() > 20)
                    isLongList = true;
            }
            String queryXjoin;
            if (!isLongList || null == inner.getLevel())
            {
                queryXjoin=queryCrossjoin(outerLevel, inner);
            }
            else
            {
                // just cache for all members of the level
                queryXjoin = queryCrossjoin(outerLevel, inner.getLevel());
            }

            try (CellSet cs = execute(queryXjoin))
            {
                List<Position> rowPositions = cs.getAxes().get(1).getPositions();
                for (int row=0 ; row<rowPositions.size() ; row++)
                {
                    Position p = rowPositions.get(row);
                    Member outerMember = p.getMembers().get(0);
                    Member sub = p.getMembers().get(1);
                    double value = cs.getCell(row).getDoubleValue();
                    if (0.0 != value)
                    {
                        assert same(outerMember.getLevel(), outerLevel);
                        MemberSet s = sets.get(sub.getUniqueName());
                        if (null == s)
                        {
                            s = new MemberSet();
                            sets.put(sub.getUniqueName(),s);
                        }
                        Member m = getCubeMember(outerMember);
                        if (null == m)
                            logWarn("Unexpected member in cellset result: " + outerMember.getUniqueName());
                        else
                            s.add(m);
                    }
                }
            }
            for (Member sub : inner.getCollection())
            {
                String query = queryIsNotEmpty(outerLevel, sub);
                MemberSet s = sets.get(sub.getUniqueName());
                if (null != s)
                    resultsCachePut(query,s);
            }
        }

        //
        // Members returned by the CellSet are not necessarily the same as those in _cube.
        //
        Member getCubeMember(Member member) throws OlapException
        {
            Level l = levelMap.get(member.getLevel().getUniqueName());
            Member m = ((NamedList<Member>)l.getMembers()).get(member.getUniqueName());
            return m;
        }
    }


    boolean same(MetadataElement a, MetadataElement b)
    {
        return a==b || a.getUniqueName().equalsIgnoreCase(b.getUniqueName());
    }



    class SqlDataSourceHelper implements IDataSourceHelper
    {

        RolapCubeDef.LevelDef getRolapFromCube(Level level)
        {
            RolapCubeDef.LevelDef ret = rolap.getRolapDef(level);
            if (null == ret)
                return null;
            return ret;
        }
        RolapCubeDef.HierarchyDef getRolapFromCube(Hierarchy h)
        {
            RolapCubeDef.HierarchyDef ret = rolap.getRolapDef(h);
            if (null == ret)
                return null;
            return ret;
        }


        ResultSet execute(String query) throws SQLException
        {
            logDebug("SqlDataSourceHelper.execute(" + query + ")");
            try
            {
                QuerySchema qs = DefaultSchema.get(user, container, StringUtils.defaultString(rolap.getSchemaName(),"core"));
                return QueryService.get().select(qs, query, true, false);
            }
            catch (SQLException|QueryParseException|AssertionError x)
            {
                logError("SqlDataSourceHelper.execute(" + query + ")", x);
                throw x;
            }
        }

        String queryCrossjoin(Result from, Result to) throws OlapException
        {
            // shouldn't call for simple level query
            if (from instanceof MemberSetResult && null != ((MemberSetResult)from).level)
            {
                return queryCrossjoin( ((MemberSetResult)from).level, to);
            }

            throw new NotImplementedException();
        }


        String queryCrossjoin(Level from, Result to) throws OlapException
        {
            if (to instanceof MemberSetResult && null != ((MemberSetResult)to).level)
            {
                return queryCrossjoin(from, ((MemberSetResult)to).level);
            }

            RolapCubeDef.LevelDef ldefFrom = getRolapFromCube(from);

            RolapCubeDef.LevelDef ldefTo = getRolapFromCube(to.getLevel());
            if (null == ldefTo && !to.getCollection().isEmpty())
                throw new UnsupportedOperationException();

            StringBuilder sb = new StringBuilder("SELECT DISTINCT ");
            sb.append(ldefFrom.getAllColumnsSQL());
            if (null != ldefTo)
            {
                sb.append(", ");
                sb.append(ldefTo.getAllColumnsSQL());
            }
            sb.append("\nFROM ");
            sb.append(rolap.getFromSQL(ldefFrom, ldefTo));
            sb.append("\nWHERE ");
            String or = "";
            Collection<Member> coll = to.getCollection();
            if (coll.isEmpty())
            {
                sb.append("(0=1)");
            }
            else
            {
                for (Member m : to.getCollection())
                {
                    sb.append(or);
                    sb.append("(").append(ldefTo.getMemberFilter(m,dialect)).append(")");
                    or = " OR ";
                }
            }

            return sb.toString();
        }


        String queryCrossjoin(Level from, Level to)
        {
            RolapCubeDef.LevelDef defFrom = getRolapFromCube(from);

            if (to.getLevelType() == Level.Type.ALL)
            {
                StringBuilder sb = new StringBuilder("SELECT DISTINCT ");
                sb.append(defFrom.getAllColumnsSQL());
                sb.append("\nFROM ");
                sb.append(rolap.getFromSQL(defFrom));
                return sb.toString();
            }
            else
            {
                RolapCubeDef.LevelDef defTo = getRolapFromCube(to);
                StringBuilder sb = new StringBuilder("SELECT DISTINCT ");
                sb.append(defFrom.getAllColumnsSQL());
                sb.append(", ");
                sb.append(defTo.getAllColumnsSQL());
                sb.append("\nFROM ");
                sb.append(rolap.getFromSQL(defFrom, defTo));
                return sb.toString();
            }
        }


        String queryIsNotEmpty(Level from, Member m)
        {
            if (from.getLevelType() == Level.Type.ALL)
            {
                throw new IllegalStateException();
            }

            RolapCubeDef.LevelDef ldefFrom = getRolapFromCube(from);

            StringBuilder sb = new StringBuilder("SELECT DISTINCT ");
            sb.append(ldefFrom.getAllColumnsSQL());

            if (m.isAll())
            {
                sb.append("\nFROM ");
                sb.append(rolap.getFromSQL(ldefFrom));
            }
            else // if (!m.isAll())
            {
                RolapCubeDef.LevelDef ldefMember = getRolapFromCube(m.getLevel());

                sb.append("\nFROM ");
                sb.append(rolap.getFromSQL(ldefFrom, ldefMember));
                sb.append("\nWHERE ");
                sb.append(ldefMember.getMemberFilter(m,dialect));
            }
            return sb.toString();
        }


        String queryWhere(MemberSetResult from, CrossResult where) throws OlapException
        {
            RolapCubeDef.LevelDef defFrom = getRolapFromCube(from.getLevel());

            StringBuilder sb = new StringBuilder("SELECT DISTINCT ");
            sb.append(defFrom.getAllColumnsSQL());

            Set<RolapCubeDef.HierarchyDef> hierarchyDefs = new LinkedHashSet<>();

            for (Result r : where.results)
            {
                RolapCubeDef.HierarchyDef def = getRolapFromCube(r.getHierarchy());
                hierarchyDefs.add(def);
            }
            sb.append("\nFROM ");
            sb.append(rolap.getFromSQL(hierarchyDefs));

            // TODO support more than single tuple
            String and = "\nWHERE";
            for (Result r : where.results)
            {
                Collection<Member> c = r.getCollection();
                if (c.size() != 1)
                    throw new UnsupportedOperationException("Currently only filtering by one tuble is supported");
                Iterator<Member> it = c.iterator();
                it.hasNext();
                Member m = it.next();
                RolapCubeDef.LevelDef l = getRolapFromCube(m.getLevel());
                String f = l.getMemberFilter(m,dialect);
                sb.append(and).append(f);
                and = " AND ";
            }

            return sb.toString();
        }


        @Override
        public MemberSet membersQuery(MemberSetResult outer, MemberSetResult sub) throws SQLException
        {
            MemberSet set;

            if (null != outer.level && sub instanceof MemberSetResult && null != ((MemberSetResult)sub).members && ((MemberSetResult)sub).members.size() == 1)
            {
                Iterator<Member> it = ((MemberSetResult)sub).members.iterator();
                it.hasNext();
                return membersQuery(outer.level, it.next());
            }


            if (null != outer.hierarchy)
            {
                // query by levels
                MemberSet union = new MemberSet();
                for (Level l : outer.hierarchy.getLevels())
                {
                    if (l.getLevelType() == Level.Type.ALL)
                        continue;
                    MemberSet levelSet = membersQuery(new MemberSetResult(l), sub);
                    union.addAll(levelSet);
                }
                // add in the all member
                if (!union.isEmpty())
                    union.add(outer.hierarchy.getLevels().get(0).getMembers().get(0));

                return union;
            }

            String query = queryCrossjoin(outer,sub);

            set = resultsCacheGet(query);
            if (null == set)
            {
                if (null != outer.level)
                {
                    populateCache(outer.getLevel(), sub);
                    set = new MemberSet();
                    for (Member m : sub.getCollection())
                    {
                        MemberSet inner = membersQuery(outer.level, m);
                        set.addAll(inner);
                    }
                    if (query.length() < 2000)
                        resultsCachePut(query, set);
                }
                else
                {
                    // non-optimized case
                    Level level = outer.getLevel();
                    if (null == level)
                    {
                        throw new NotImplementedException();
                    }
                    RolapCubeDef.LevelDef ldef = getRolapFromCube(level);

                    try (ResultSet rs = execute(query))
                    {
                        set = new MemberSet();
                        while (rs.next())
                        {
                            String uniqueName = ldef.getMemberUniqueNameFromResult(rs);
                            Member m = ((NamedList<Member>)level.getMembers()).get(uniqueName);
                            set.add(m);
                        }
                    }
                    resultsCachePut(query, set);
                }
            }

            return set;
        }


        @Override
        public MemberSet membersQuery(Level outer, Member sub) throws SQLException
        {
            if (same(sub.getHierarchy(), outer.getHierarchy()))
            {
                MemberSet s = new MemberSet();
                addMemberAndChildrenInLevel(sub, outer, s);
                return s;
            }

            if (sub.isAll())
            {
                return new MemberSet(outer, outer.getMembers());
            }
            else if (mondrianCompatibleNullHandling && "#null".equals(sub.getName()))
            {
                // there's no particularly easy way to compute this, compute the whole dang level..
                populateCache(outer, new MemberSetResult(sub.getLevel()));
                String query = queryIsNotEmpty(outer, sub);
                MemberSet s = resultsCacheGet(query);
                if (null != s)
                    return s;
            }
//            else
            {
                String query = queryIsNotEmpty(outer, sub);
                MemberSet s = resultsCacheGet(query);
                if (null != s)
                    return s;

                MemberSet set = new MemberSet();

                try (ResultSet rs = execute(query))
                {
                    RolapCubeDef.LevelDef ldef = getRolapFromCube(outer);
                    while (rs.next())
                    {
                        String uniqueName = ldef.getMemberUniqueNameFromResult(rs);
                        Member m = ((NamedList<Member>) outer.getMembers()).get(uniqueName);
                        if (null == m)
                        {
                            logWarn("member not found: " + uniqueName);
                            continue;
                        }
                        set.add(m);
                    }
                }
                resultsCachePut(query, set);
                return set;
            }
        }


        @Override
        public MemberSet membersQuery(MemberSetResult outer, CrossResult sub) throws SQLException
        {
            String query = queryWhere(outer, sub);
            MemberSet s = resultsCacheGet(query);
            if (null != s)
                return s;

            try (ResultSet rs = execute(query))
            {
                Level level = outer.getLevel();
                RolapCubeDef.LevelDef ldef = getRolapFromCube(level);
                MemberSet set = new MemberSet();
                while (rs.next())
                {
                    String uniqueName = ldef.getMemberUniqueNameFromResult(rs);
                    Member m = ((NamedList<Member>)level.getMembers()).get(uniqueName);
                    if (null == m)
                    {
                        logWarn("member not found: " + uniqueName);
                        continue;
                    }
                    set.add(m);
                }
                resultsCachePut(query, set);
                return set;
            }
        }


        @Override
        public void populateCache(Level outerLevel, Result inner) throws SQLException
        {
            // MDX doesn't allow union across different hierarchies, but I do, so here I iterate over
            // the union components
            if (inner instanceof UnionResult)
            {
                for (Result r : ((UnionResult) inner).results)
                    populateCache(outerLevel, r);
                return;
            }
            else
            {
                if (inner instanceof MemberSetResult && null != ((MemberSetResult)inner).hierarchy)
                {
                    Hierarchy h = ((MemberSetResult)inner).hierarchy;
                    for (Level l : h.getLevels())
                        populateCache(outerLevel, new MemberSetResult(l));
                    return;
                }
            }

            // if this is an ALL level, pre-populating might not be that useful
            if (null != inner.getLevel() && inner.getLevel().getLevelType() == Level.Type.ALL)
                return;

            boolean cacheMiss = false;

            // We cache by individual members, but we don't necessaryily want to load the cache one set at a time
            // we could find just the members that are not yet cached, but I'm just going to check if ANY are missing
            for (Member sub : inner.getCollection())
            {
                if (same(sub.getHierarchy(), outerLevel.getHierarchy()))
                    continue;
                String query = queryIsNotEmpty(outerLevel, sub);
                MemberSet s = resultsCacheGet(query);
                if (null == s)
                {
                    cacheMiss = true;
                    break;
                }
            }
            if (!cacheMiss)
                return;

            CaseInsensitiveHashMap<MemberSet> sets = new CaseInsensitiveHashMap<>();
            Collection<Member> innerCollection = inner.getCollection();
            for (Member sub : innerCollection)
                sets.put(sub.getUniqueName(), new MemberSet());

            boolean isShortList = false;
            if (inner instanceof MemberSetResult)
            {
                MemberSetResult msr = (MemberSetResult)inner;
                if (msr.members != null && msr.members.size() <= 20)
                    isShortList = true;
            }

            String queryXjoin;
            if (isShortList || null == inner.getLevel())
            {
                queryXjoin=queryCrossjoin(outerLevel, inner);
            }
            else
            {
                // just cache for all members of the level
                queryXjoin = queryCrossjoin(outerLevel, inner.getLevel());
            }

            Member nullMember = null;
            MemberSet nullMemberSet = null;
            String nullQuery = null;

            try (ResultSet rs = execute(queryXjoin))
            {
                RolapCubeDef.LevelDef ldefOuter = getRolapFromCube(outerLevel);
                RolapCubeDef.LevelDef ldefInner = getRolapFromCube(inner.getLevel());

                //int[] getMemberIndexes(rs,leveldef);
                while (rs.next())
                {
                    String outerUniqueName = ldefOuter.getMemberUniqueNameFromResult(rs);
                    Member outerMember = ((NamedList<Member>) outerLevel.getMembers()).get(outerUniqueName);
                    if (null == outerMember)
                    {
                        logWarn("member not found: " + outerUniqueName);
                        continue;
                    }
                    String subUniqueName = ldefInner.getMemberUniqueNameFromResult(rs);
                    Member sub = ((NamedList<Member>)inner.getLevel().getMembers()).get(subUniqueName);
                    if (null == sub)
                    {
                        if (!subUniqueName.endsWith("[#null]"))
                            logWarn("member not found: " + subUniqueName);
                        continue;
                    }
                    MemberSet s = sets.get(sub.getUniqueName());
                    if (null == s)
                    {
                        s = new MemberSet();
                        sets.put(sub.getUniqueName(),s);
                    }
                    if ("#null".equals(sub.getName()))
                    {
                        nullMember = sub;
                        nullMemberSet = s;
                    }
                    s.add(outerMember);
                }
            }

            for (Member sub : inner.getCollection())
            {
                String query = queryIsNotEmpty(outerLevel, sub);
                MemberSet s = sets.get(sub.getUniqueName());
                if (null == s)
                    continue;
                if (mondrianCompatibleNullHandling)
                {
                    if (sub == nullMember)
                    {
                        nullQuery = query;
                        continue;
                    }
                    if (null != nullMember)
                    {
                        nullMemberSet.removeAll(s);
                    }
                }
                resultsCachePut(query,s);
            }

            // if we skipped null member, add it now
            if (mondrianCompatibleNullHandling && null != nullMember)
            {
                resultsCachePut(nullQuery,nullMemberSet);
            }
        }
    }


    class DoubleDownHelper implements IDataSourceHelper
    {
        @Override
        public MemberSet membersQuery(MemberSetResult outer, MemberSetResult sub) throws SQLException
        {
            MemberSet mdx = _cdsh.membersQuery(outer,sub);
            MemberSet sql = _sdsh.membersQuery(outer,sub);
            if (!sql.equals(mdx))
            {
                logWarn(outer + " x " + sub);
            }
            return sql;
        }

        @Override
        public MemberSet membersQuery(Level outer, Member sub) throws SQLException
        {
            MemberSet mdx = _cdsh.membersQuery(outer,sub);
            MemberSet sql = _sdsh.membersQuery(outer,sub);
            if (!sql.equals(mdx))
            {
                MemberSet inMdxButNotSql = new MemberSet(mdx);
                inMdxButNotSql.removeAll(sql);

                MemberSet inSqlButNotMdx = new MemberSet(sql);
                inSqlButNotMdx.removeAll(mdx);

                if (!"#null".equals(sub.getName()))
                    logWarn(outer.getUniqueName() + " x " + sub.getUniqueName());
            }
            return sql;
        }

        @Override
        public MemberSet membersQuery(MemberSetResult outer, CrossResult sub) throws SQLException
        {
            MemberSet mdx = _cdsh.membersQuery(outer,sub);
            MemberSet sql = _sdsh.membersQuery(outer,sub);
            if (!sql.equals(mdx))
            {
                logWarn(outer + " x " + sub);
            }
            return sql;
        }

        @Override
        public void populateCache(Level outerLevel, Result inner) throws SQLException
        {
            _sdsh.populateCache(outerLevel,inner);
            _cdsh.populateCache(outerLevel,inner);
        }
    }


    class _CellSet extends QubeCellSet
    {
        _CellSet(List<Number> results, Collection<Member> rows, Collection<Member> cols)
        {
            super(BitSetQueryImpl.this.cube, BitSetQueryImpl.this.measure, results, rows, cols);
        }
    }


    class _CountDistinctMeasure implements org.olap4j.metadata.Member
    {
        String _name;

        _CountDistinctMeasure(Level level)
        {
            _name = level.getDimension().getName() + "Count";
        }

        @Override
        public NamedList<? extends Member> getChildMembers() throws OlapException
        {
            return null;
        }

        @Override
        public int getChildMemberCount() throws OlapException
        {
            return 0;
        }

        @Override
        public Member getParentMember()
        {
            return null;
        }

        @Override
        public Level getLevel()
        {
            return cube.getMeasures().get(0).getLevel();
        }

        @Override
        public Hierarchy getHierarchy()
        {
            return cube.getMeasures().get(0).getHierarchy();
        }

        @Override
        public Dimension getDimension()
        {
            return cube.getMeasures().get(0).getDimension();
        }

        @Override
        public Type getMemberType()
        {
            return null;
        }

        @Override
        public boolean isAll()
        {
            return false;
        }

        @Override
        public boolean isChildOrEqualTo(Member member)
        {
            return false;
        }

        @Override
        public boolean isCalculated()
        {
            return false;
        }

        @Override
        public int getSolveOrder()
        {
            return 0;
        }

        @Override
        public ParseTreeNode getExpression()
        {
            return null;
        }

        @Override
        public List<Member> getAncestorMembers()
        {
            return null;
        }

        @Override
        public boolean isCalculatedInQuery()
        {
            return false;
        }

        @Override
        public Object getPropertyValue(Property property) throws OlapException
        {
            return null;
        }

        @Override
        public String getPropertyFormattedValue(Property property) throws OlapException
        {
            return null;
        }

        @Override
        public void setProperty(Property property, Object o) throws OlapException
        {

        }

        @Override
        public NamedList<Property> getProperties()
        {
            return null;
        }

        @Override
        public int getOrdinal()
        {
            return -1;
        }

        @Override
        public boolean isHidden()
        {
            return false;
        }

        @Override
        public int getDepth()
        {
            return 1;
        }

        @Override
        public Member getDataMember()
        {
            return null;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        @Override
        public String getUniqueName()
        {
            return "[Measures].[" + _name + "]";
        }

        @Override
        public String getCaption()
        {
            return null;
        }

        @Override
        public String getDescription()
        {
            return null;
        }

        @Override
        public boolean isVisible()
        {
            return false;
        }
    }

    static public void invalidateCache(Container c)
    {
        _resultsCache.removeUsingPrefix( "" + c.getRowId() + "/");
    }
    static public void invalidateCache(OlapSchemaDescriptor sd)
    {
        _resultsCache.clear();
    }
    static public void invalidateCache()
    {
        _resultsCache.clear();
    }


    static void logDebug(String msg)
    {
        _log.debug(msg);
    }
    static void logWarn(String msg)
    {
        _log.warn(msg);
    }
    static void logError(String msg, Throwable x)
    {
        _log.error(msg, x);
    }
}
