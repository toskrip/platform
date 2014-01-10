/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaTreeNode;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Matthew
 * Date: Apr 27, 2006
 * Time: 11:29:43 AM
 */
public interface TableInfo extends HasPermission, SchemaTreeNode
{

    String getName();

    /** Get title or name, if title is null **/
    String getTitle();

    /** Get title field, whether null or not **/
    @Nullable
    String getTitleField();

    /**
     * simple name that can be used directly in SQL statement
     *
     * use only for tables known to be real database tables, usually
     * for INSERT/UPDATE/DELETE. For SELECT use getFromSQL(alias).
     */
    @Nullable
    String getSelectName();

    /**
     * SQL representing this table, e.g.
     *     "Issues.Issues <alias>"
     *     "(SELECT * FROM Issues.Issues WHERE Container='...') <alias>"
     **/
    @NotNull  // TODO: Override in QueryPivot.PivotTableInfo returns null!
    SQLFragment getFromSQL(String alias);

    /* For most tables this is the same as getFromSQL().
     * However, for some tables that want to help optimize generated SQL
     * we can ask for the columns we need to be accessible.
     *
     * Only columns returned by getColumn() or resolveColumn() should be handed in (no lookups)
     *
     * This is really intended for QueryTable
     */
    SQLFragment getFromSQL(String alias, Set<FieldKey> cols);

    DbSchema getSchema();

    @Nullable
    UserSchema getUserSchema();

    /** getSchema().getSqlDialect() */
    SqlDialect getSqlDialect();

    List<String> getPkColumnNames();

    @NotNull List<ColumnInfo> getPkColumns();

    ColumnInfo getVersionColumn();

    String getVersionColumnName();

    /** @return the default display value for this table if it's the target of a foreign key */
    String getTitleColumn();

    boolean hasDefaultTitleColumn();

    DatabaseTableType getTableType();

    /** Get select list for primary column to title column. */
    NamedObjectList getSelectList();

    /** Get select list for named (hopefully unique!) column to title column. */
    NamedObjectList getSelectList(String columnName);

    ColumnInfo getColumn(String colName);

    // same as getColumn(colName.getName()), does not need to support lookup columns
    ColumnInfo getColumn(FieldKey colName);

    List<ColumnInfo> getColumns();

    List<ColumnInfo> getUserEditableColumns();

    /** @param colNames comma separated column names */
    List<ColumnInfo> getColumns(String colNames);

    List<ColumnInfo> getColumns(String... colNameArray);

    Set<String> getColumnNameSet();

    /**
     * Return a list of ColumnInfos that make up the extended set of
     * columns that could be considered a part of this table by default.
     * For the majority of tables, this is simply the columns returned from
     * {@link TableInfo#getColumns()} plus any additional columns from
     * {@link TableInfo#getDefaultVisibleColumns()).
     *
     * For other tables, the extended column set could include some columns
     * from lookup tables.
     *
     * @param hidden Include hidden columns.
     * @return All columns.
     */
    Map<FieldKey, ColumnInfo> getExtendedColumns(boolean hidden);

    List<FieldKey> getDefaultVisibleColumns();

    void setDefaultVisibleColumns(Iterable<FieldKey> keys);

    ButtonBarConfig getButtonBarConfig();

    AggregateRowConfig getAggregateRowConfig();

    /**
     * Return the default query grid view URL for the table or null.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#executeQuery} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getGridURL(Container container);

    /**
     * Return the insert URL expression for the table or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default insert view will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#insertQueryRow} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getInsertURL(Container container);

    /**
     * Return the import URL expression for the table or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default import view will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#importData} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getImportDataURL(Container container);

    /**
     * Return the delete URL expression for the table or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default action will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#deleteQueryRows} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getDeleteURL(Container container);

    /**
     * Return the update URL expression for a particular record or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default update view will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#updateQueryRow} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     * @param columns if null, implementations should simply return their first (and potentially only) details URL,
     *                or null if they don't have one.
     *                If non-null, the set of columns that are available from the ResultSet so that an appropriate
     *                URL can be chosen
     */
    StringExpression getUpdateURL(@Nullable Set<FieldKey> columns, Container container);

    /**
     * Return the details URL expression for a particular record or null.
     * @param columns if null, implementations should simply return their first (and potentially only) details URL,
     *                or null if they don't have one.
     *                If non-null, the set of columns that are available from the ResultSet so that an appropriate
     *                URL can be chosen
     */
    StringExpression getDetailsURL(@Nullable Set<FieldKey> columns, Container container);
    boolean hasDetailsURL();
    public Set<FieldKey> getDetailsURLKeys();

    boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm);

    /**
     * Return the method of a given name.  Methods are accessible via the QueryModule's query
     * language.  Most tables do not have methods. 
     */
    MethodInfo getMethod(String name);

    /**
     * Returns a string that will appear on the default import page and as the top line
     * of the default excel template
     */
    public String getImportMessage();

    /**
     * Returns a list of templates (label / URL) that should be used as the options for excel upload
     * Each URL should either point to a static template file or an action to generate the template.
     * If no custom templates have been provided, it will return the default URL
     */
    public List<Pair<String, String>> getImportTemplates(ViewContext ctx);

    /**
     * Returns a list of the raw import templates (without substituting the container).  This is intended to be
     * used by FilteredTable or other instances that need to copy the raw values from a parent table.  In general,
     * getImportTemplates() should be used instead
     */
    public List<Pair<String, StringExpression>> getRawImportTemplates();

    public boolean isPublic();

    public String getPublicName();

    public String getPublicSchemaName();

    public boolean needsContainerClauseAdded();

    public ContainerFilter getContainerFilter();

    /**
     * Finds and applies the first metadata xml from active modules in the schema's container and then the first user-created metadata in the container hierarchy.
     *
     * @see QueryService#findMetadataOverride(UserSchema, String, boolean, boolean, Collection, Path)
     *
     * @param tableName
     * @param schema
     * @param errors
     */
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors);

    public void overlayMetadata(Collection<TableType> metadata, UserSchema schema, Collection<QueryException> errors);

    public boolean isMetadataOverrideable();

    public ColumnInfo getLookupColumn(ColumnInfo parent, String name);

    public int getCacheSize();

    public String getDescription();

    /**
     * Get Domain associated with this TableInfo if any.
     */
    @Nullable
    public Domain getDomain();

    /**
     * Get DomainKind associated with this TableInfo if any.
     * Domain may or may not exist even if DomainKind is available.
     */
    @Nullable
    public DomainKind getDomainKind();

    /**
     * Returns a QueryUpdateService implementation for this TableInfo,
     * or null if the table/query is not updatable.
     * @return A QueryUpdateService implementation for this table/query or null.
     */
    @Nullable
    QueryUpdateService getUpdateService();

    public enum TriggerType
    {
        INSERT, UPDATE, DELETE, SELECT, TRUNCATE;

        public String getMethodName()
        {
            String name = name();
            return name.substring(0, 1) + name.toLowerCase().substring(1, name.length());
        }
    }


    /**
     * Queries may have named parameters, SELECT queries (the only kind we have right now) may
     * return TableInfos.  This is how you find out if a TableInfo representing a query has named
     * parameters
     */

    @NotNull
    public Collection<QueryService.ParameterDecl> getNamedParameters();

    /**
     * Executes any trigger scripts for this table.
     *
     * The trigger should be called once before and once after an entire set of rows for each of the
     * INSERT, UPDATE, DELETE trigger types.  A trigger script may set up data structures to be used
     * during validation.  In particular, the trigger script might want to do a query to populate a set of
     * legal values.
     * <p>
     * The <code>errors</code> parameter holds validation error messages for the entire row set.
     * If errors are created during the row level trigger script, they should be added as nested ValidationExceptions.
     * The ValidationException will be thrown after executing the trigger scripts if it contains any errors.
     * <p>
     * Example usage:
     * <pre>
     *   ValidationException errors = new ValidationException();
     *   getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, true, errors);
     *
     *   List&lt;Map&lt;String, Object>> result = new ArrayList&lt;Map&lt;String, Object>>(rows.size());
     *   for (int i = 0; i &lt; rows.size(); i++)
     *   {
     *       try
     *       {
     *           Map<String, Object> row = rows.get(i);
     *           Map<String, Object> oldRow = getRow( ... );
     *           if (oldRow == null)
     *               throw new NotFoundException("The existing row was not found.");
     *
     *           getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.UPDATE, true, i, row, oldRow);
     *           Map<String, Object> updatedRow = updateRow(user, container, row, oldRow);
     *           if (updatedRow == null)
     *               continue;
     *
     *           getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.UPDATE, false, i, updatedRow, oldRow);
     *           result.add(updatedRow);
     *       }
     *       catch (ValidationException vex)
     *       {
     *           errors.addNested(vex);
     *       }
     *   }
     *
     *   // Firing the after batch trigger will throw a ValidationException if
     *   // any errors were generated during the row triggers or the during after batch trigger.
     *   getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, false, errors);
     * </pre>
     *
     * @param c The current Container.
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param errors Any errors created by the validation script will be added to the errors collection.
     * @param extraContext Optional additional bindings to set in the script's context when evaluating.
     * @throws BatchValidationException if the trigger function returns false or the errors map isn't empty.
     */
    public void fireBatchTrigger(Container c, TriggerType type, boolean before, BatchValidationException errors, Map<String, Object> extraContext)
            throws BatchValidationException;

    /**
     * Fire trigger for a single row.
     * <p>
     * The trigger is called once before and once after each row for each of the INSERT, UPDATE, DELETE
     * trigger types.
     * <p>
     * The following table describes the parameters for each of the trigger types:
     * <dl>
     *   <dt><code>INSERT</code>:
     *   <dd><ul>
     *       <li>before: <code>newRow</code> contains the row values to be inserted, <code>oldRow</code> is null.
     *       <li>after: <code>newRow</code> contains the inserted row values, <code>oldRow</code> is null.
     *       </li>
     *
     *   <dt><code>UPDATE</code>:
     *   <dd><ul>
     *       <li>before: <code>newRow</code> contains the row values to be updated, <code>oldRow</code> contains the previous version of the row.
     *       <li>after: <code>newRow</code> contains the updated row values, <code>oldRow</code> contains the previous version of the row.
     *       </ul>
     *
     *   <dt><code>DELETE</code>:
     *   <dd><ul>
     *       <li>before: <code>oldRow</code> contains the previous version of the row.
     *       <li>after: <code>newRow</code> is null, <code>oldRow</code> contains the previous version of the row.
     *       </li>
     * </dl>
     *
     * @param c The current Container.
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param oldRow The previous row for UPDATE and DELETE
     * @param newRow The new row for INSERT and UPDATE.
     * @param extraContext Optional additional bindings to set in the script's context when evaluating.
     * @throws ValidationException if the trigger function returns false or the errors map isn't empty.
     */
    public void fireRowTrigger(Container c, TriggerType type, boolean before, int rowNumber,
                                  @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, Map<String, Object> extraContext)
            throws ValidationException;

    public boolean hasTriggers(Container c);


    /**
     * TableInfos that can be associated with a DbCache need a reliable key other than a TableInfo instance.
     * Return null if DbCache is not supported.
     *
     * We should probably kill DbCache, but let's fix this for now (https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=10508)
     */
    public Path getNotificationKey();

    /* for asserting that tableinfo is not changed unexpectedly */
    public void setLocked(boolean b);
    public boolean isLocked();

    public boolean supportsContainerFilter();

    /**
     * Returns a ContainerContext for this table or null if ContainerContext is not supported.
     *
     * @return The ContainerContext for this table or null.
     */
    @Nullable
    public ContainerContext getContainerContext();

    public void setAuditBehavior(AuditBehaviorType type);
    public AuditBehaviorType getAuditBehavior();

    /**
     * Returns the row primary key column to use for audit history details. Note, this must
     * be a single key as we don't support multiple column primary keys for audit details.
     */
    @Nullable
    public FieldKey getAuditRowPk();
}
