/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.constant', {
    singleton: true,
    STATE_FILTER: 'statefilter',
    SELECTION_FILTER: 'stateSelectionFilter'
});

Ext.define('LABKEY.app.model.State', {

    extend : 'Ext.data.Model',

    fields : [
        {name : 'activeView'},
        {name : 'viewState'},
        {name : 'customState'},
        {name : 'filters'},
        {name : 'selections'},
        {name : 'detail'}
    ],

    proxy : {
        type : 'sessionstorage',
        id   : 'connectorStateProxy'
    }
});

Ext.define('LABKEY.app.controller.State', {

    extend : 'Ext.app.Controller',

    requires : [
        'LABKEY.app.model.State'
    ],

    preventRedundantHistory: true,

    subjectName: '',

    _ready: false,

    init : function() {

        if (LABKEY.devMode) {
            STATE = this;
        }

        this.olap = this.application.olap;

        if (LABKEY.devMode) {
            this.onMDXReady(function(mdx) { MDX = mdx; });
        }

        this.state = Ext.create('Ext.data.Store', {
            model : 'LABKEY.app.model.State'
        });

        this.customState = {};
        this.filters = []; this.selections = [];
        this.privatefilters = {};

        this.state.load();

        this.application.on('route', function() { this.loadState(); }, this, {single: true});
    },

    getCurrentState : function() {
        if (this.state.getCount() > 0) {
            return this.state.getAt(this.state.getCount()-1);
        }
    },

    getPreviousState : function() {
        var index = -1;
        if (this.state.getCount() > 1) {
            index = this.state.getCount()-2;
        }
        return index;
    },

    onMDXReady : function(callback, scope) {
        var s = scope || this;
        this.olap.onReady(callback, s);
    },

    loadState : function(idx) {

        if (!idx) {
            idx = this.state.getCount()-1; // load most recent state
        }

        if (idx >= 0) {
            var s = this.state.getAt(idx).data;

            // Apply state
            Ext.apply(this, s.viewState);

            if (s.customState) {
                this.customState = s.customState;
            }

            // Apply Filters
            if (Ext.isArray(s.filters) && s.filters.length > 0) {

                var _filters = [];
                Ext.each(s.filters, function(_f) {
                    _filters.push(_f);
                });

                this.setFilters(_filters, true);
            }

            // Apply Selections
            if (s.selections && s.selections.length > 0) {
                this.setSelections(s.selections, true);
            }
        }

        this.manageState();

        this._ready = true;
        this.application.fireEvent('stateready', this);
    },

    onReady : function(callback, scope) {
        if (Ext.isFunction(callback)) {
            if (this._ready === true) {
                callback.call(scope, this);
            }
            else {
                this.application.on('stateready', function() {
                    callback.call(scope, this);
                }, this, {single: true});
            }
        }

    },

    manageState : function() {
        var size = this.state.getCount();
        if (size > 10) {
            var recs = this.state.getRange(size-5, size-1);
            this.state.removeAll();
            this._sync();
            this.state.getProxy().clear();
            this._sync(recs);
        }
    },

    /**
     * Managed sync that attempts to recover in case the storage fails.
     * Ideally, this should be pushed down into the extended store instance in it's own
     * sync method.
     * @param records
     * @private
     */
    _sync : function(records) {

        try
        {
            if (Ext.isArray(records)) {
                this.state.add(records);
            }
            this.state.sync();
        }
        catch (e)
        {
            if (this.__LOCK__ !== true) {
                this.__LOCK__ = true;
                this.manageState();
                this._sync(records);
                this.__LOCK__ = false;
            }
        }
    },

    getState : function(lookup, defaultState) {
        if (this.state.getCount() > 0) {
            var s = this.state.getAt(this.state.getCount()-1);
            if (s.customState && s.customState[lookup.view]) {
                if (s.customState[lookup.view].hasOwnProperty(lookup.key)) {
                    return s.customState[lookup.view][lookup.key];
                }
            }
        }
        return defaultState;
    },

    findState : function(fn, scope, startIndex) {

        if (this.state.getCount() > 0) {
            var idx = this.state.getCount() - 1;
            var _scope = scope || this;

            if (startIndex && startIndex < idx)
                idx = startIndex;

            var rec = this.state.getAt(idx).data;
            while (!fn.call(_scope, idx, rec) && idx > 0) {
                idx--;
                rec = this.state.getAt(idx).data;
            }
            return idx;
        }
        return -1;
    },

    setCustomState : function(lookup, state) {
        if (!this.customState.hasOwnProperty(lookup.view))
            this.customState[lookup.view] = {};
        this.customState[lookup.view][lookup.key] = state;
    },

    getCustomState : function(view, key) {
        var custom = undefined;
        if (this.customState.hasOwnProperty(view)) {
            custom = this.customState[view][key];
        }
        return custom;
    },

    /**
     * Provided to be overridden to provide a custom title for view states.
     * @param viewname
     * @returns {*}
     */
    getTitle : function(viewname) {
        return viewname;
    },

    updateState : function() {

        // prepare filters
        var jsonReadyFilters = [];
        Ext.each(this.filters, function(f) {
            jsonReadyFilters.push(f.jsonify());
        });

        // prepare selections
        var jsonReadySelections = [];
        Ext.each(this.selections, function(s) {
            jsonReadySelections.push(s.jsonify());
        });

        this._sync([{
            viewState: {},
            customState: this.customState,
            filters: jsonReadyFilters,
            selections: jsonReadySelections
        }]);
    },

    /**
     * This method allows for updating a filter that is already being tracked.
     * Given a filter id, the datas parameter will replace that filter's value for
     * the given key in datas. Note: This will only replace those values specified
     * leaving all other values on the filter as they were.
     * @param id
     * @param datas
     */
    updateFilter : function(id, datas) {

        Ext.each(this.filters, function(filter) {
            if (filter.id === id) {

                Ext.iterate(datas, function(key, val) {
                    filter.set(key, val);
                });

                filter.commit();

                this.requestFilterUpdate(false);
            }
        }, this);
    },

    /**
     * This method allows for updating a selection that is already being tracked.
     * Given a selection id, the datas parameter will replace that selection's value for
     * the given key in datas. Note: This will only replace those values specified
     * leaving all other values on the selection as they were.
     * @param id
     * @param datas
     */
    updateSelection : function(id, datas) {

        Ext.each(this.selections, function(selection) {
            if (selection.id === id) {

                Ext.iterate(datas, function(key, val) {
                    selection.set(key, val);
                });

                selection.commit();

                this.requestSelectionUpdate(false);
            }
        }, this);
    },

    _is : function(filterset, id) {
        var found = false;
        Ext.each(filterset, function(f) {
            if (id === f.id) {
                found = true;
                return false;
            }
        });
        return found;
    },

    isFilter : function(id) {
        return this._is(this.filters, id);
    },

    isSelection : function(id) {
        return this._is(this.selections, id);
    },

    /**
     * You must call updateFilterMembersComplete() once done updating filter members
     * @param id
     * @param members
     * @param skipState
     */
    updateFilterMembers : function(id, members) {
        for (var f=0; f < this.filters.length; f++) {
            if (this.filters[f].id == id)
            {
                this.filters[f].set('members', members);
            }
        }
    },

    updateFilterMembersComplete : function(skipState, callback, scope) {
        this.requestFilterUpdate(skipState, false, true, callback, scope);
        // since it is silent we need to update the count seperately
        this.updateFilterCount();
    },

    getFilters : function(flat) {
        if (!this.filters || this.filters.length == 0)
            return [];

        if (!flat)
            return this.filters;

        var flatFilters = [];

        for (var f=0; f < this.filters.length; f++) {
            var data = Ext.clone(this.filters[f].data);
            flatFilters.push(data);
        }

        return flatFilters;
    },

    getFilterModelName : function() {
        console.error('Failed to get filter model name.');
    },

    _getFilterSet : function(filters) {

        var newFilters = [],
                filterClass = this.getFilterModelName();

        for (var s=0; s < filters.length; s++) {
            var f = filters[s];

            // decipher object structure
            if (!f.$className) {
                if (f.data) {
                    newFilters.push(Ext.create(filterClass, f.data));
                }
                else {
                    newFilters.push(Ext.create(filterClass, f));
                }
            }
            else if (f.$className == filterClass) {
                newFilters.push(f);
            }
        }
        return newFilters;

    },

    hasFilters : function() {
        return this.filters.length > 0;
    },

    addFilter : function(filter, skipState) {
        return this.addFilters([filter], skipState);
    },

    addFilters : function(filters, skipState, clearSelection, callback, scope) {
        var _f = this.getFilters();
        if (!_f)
            _f = [];

        var newFilters = this._getFilterSet(filters);

        // new filters are always appended
        for (var f=0; f < newFilters.length; f++)
            _f.push(newFilters[f]);

        this.filters = _f;

        if (clearSelection)
            this.clearSelections(true);

        this.requestFilterUpdate(skipState, false, false, callback, scope);

        return newFilters;
    },

    prependFilter : function(filter, skipState, callback, scope) {
        this.setFilters([filter].concat(this.filters), skipState, callback, scope);
    },

    loadFilters : function(stateIndex) {
        var previousState =  this.state.getAt(stateIndex);
        if (Ext.isDefined(previousState)) {
            var filters = previousState.get('filters');
            this.setFilters(filters);
        }
        else {
            console.warn('Unable to find previous filters: ', stateIndex);
        }
    },

    setFilters : function(filters, skipState, callback, scope) {
        this.filters = this._getFilterSet(filters);
        this.requestFilterUpdate(skipState, false, false, callback, scope);
    },

    clearFilters : function(skipState) {
        this.filters = [];
        this.requestFilterUpdate(skipState, false);
    },

    _removeHelper : function(target, filterId, hierarchyName, uniqueName) {

        var filterset = [];
        for (var t=0; t < target.length; t++) {

            if (target[t].id != filterId) {
                filterset.push(target[t]);
            }
            else {

                // Check if removing group/grid
                if (target[t].isGrid() || target[t].isPlot())
                    continue;

                // Found the targeted filter to be removed
                var newMembers = target[t].removeMember(uniqueName);
                if (newMembers.length > 0) {
                    target[t].set('members', newMembers);
                    filterset.push(target[t]);
                }
            }
        }

        return filterset;
    },

    removeFilter : function(filterId, hierarchyName, uniqueName) {
        var filters = this.getFilters();
        var fs = this._removeHelper(filters, filterId, hierarchyName, uniqueName);

        if (fs.length > 0) {
            this.setFilters(fs);
        }
        else {
            this.clearFilters();
        }

        this.fireEvent('filterremove', this.getFilters());
    },

    removeSelection : function(filterId, hierarchyName, uniqueName) {

        var ss = this._removeHelper(this.selections, filterId, hierarchyName, uniqueName);

        if (ss.length > 0) {
            this.addSelection(ss, false, true, true);
        }
        else {
            this.clearSelections(false);
        }

        this.fireEvent('selectionremove', this.getSelections());
    },

    addGroup : function(grp) {
        if (grp.data.filters) {
            var filters = grp.data.filters;
            for (var f=0; f < filters.length; f++) {
                filters[f].groupLabel = grp.data.label;
            }
            this.addPrivateSelection(grp.data.filters, 'groupselection');
        }
    },

    setFilterOperator : function(filterId, value) {
        for (var s=0; s < this.selections.length; s++) {
            if (this.selections[s].id == filterId) {
                this.selections[s].set('operator', value);
                this.requestSelectionUpdate(false, true);
                return;
            }
        }

        for (s=0; s < this.filters.length; s++) {
            if (this.filters[s].id == filterId) {
                this.filters[s].set('operator', value);
                this.requestFilterUpdate(false, true);
                return;
            }
        }
    },

    requestFilterUpdate : function(skipState, opChange, silent, callback, scope) {

        this.onMDXReady(function(mdx) {

            var olapFilters = [], getOlap = LABKEY.app.model.Filter.getOlapFilter;
            for (var f=0; f < this.filters.length; f++) {
                olapFilters.push(getOlap(mdx, this.filters[f].data, this.subjectName));
            }

            var proceed = true;
            for (f=0; f < olapFilters.length; f++) {
                if (olapFilters[f].arguments.length == 0) {
                    alert('EMPTY ARGUMENTS ON FILTER');
                    proceed = false;
                }
            }

            if (proceed) {
                if (olapFilters.length == 0) {
                    mdx.clearNamedFilter(LABKEY.app.constant.STATE_FILTER, function() {
                        this._filterUpdateHelper(skipState, silent, callback, scope);
                    }, this);
                }
                else {
                    mdx.setNamedFilter(LABKEY.app.constant.STATE_FILTER, olapFilters, function() {
                        this._filterUpdateHelper(skipState, silent, callback, scope);
                    }, this);
                }
            }

        }, this);
    },

    /**
     * @private
     */
    _filterUpdateHelper : function(skipState, silent, callback, scope) {
        if (!skipState) {
            this.updateState();
        }

        if (Ext.isFunction(callback)) {
            callback.call(scope || this);
        }

        if (!silent) {
            this.fireEvent('filterchange', this.filters);
        }
    },

    getSelections : function(flat) {
        if (!this.selections || this.selections.length == 0)
            return [];

        if (!flat)
            return this.selections;

        var flatSelections = [];
        for (var f=0; f < this.selections.length; f++) {
            flatSelections.push(this.selections[f].data);
        }

        return flatSelections;
    },

    hasSelections : function() {
        return this.selections.length > 0;
    },

    mergeFilters : function(newFilters, oldFilters, opFilters) {

        var match;
        for (var n=0; n < newFilters.length; n++) {

            match = false;
            for (var i=0; i < oldFilters.length; i++) {

                if (this.shouldMergeFilters(oldFilters[i], newFilters[n])) {

                    this.handleMergeRangeFilters(oldFilters[i], newFilters[n]);

                    for (var j=0; j < newFilters[n].data.members.length; j++) {

                        match = true;

                        if (!this.isExistingMemberByUniqueName(oldFilters[i].data.members, newFilters[n].data.members[j]))
                            oldFilters[i].data.members.push(newFilters[n].data.members[j]);
                    }
                }
            }

            // did not find match
            if (!match) {
                oldFilters.push(newFilters[n]);
            }
        }

        // Issue: 15359
        if (Ext.isArray(opFilters)) {

            for (n=0; n < opFilters.length; n++) {

                for (var i=0; i < oldFilters.length; i++) {

                    if (oldFilters[i].getHierarchy() == opFilters[n].getHierarchy()) {
                        var op = opFilters[n].data;
                        if (!LABKEY.app.model.Filter.dynamicOperatorTypes) {
                            op = LABKEY.app.model.Filter.lookupOperator(op);
                        }
                        else {
                            op = op.operator;
                        }
                        oldFilters[i].set('operator', op);
                    }
                }
            }
        }

        return oldFilters;
    },

    shouldMergeFilters : function(oldFilter, newFilter) {
        return (oldFilter.data.hierarchy == newFilter.data.hierarchy);
    },

    handleMergeRangeFilters : function(oldFilter, newFilter) {
        // if the old filter is a member list and the new filter is a range, drop the range from new filter and merge will be a member list
        // if the old filter is a range and the new filter is a member list, drop the range from old filter and merge will be a member list
        // else concatenate the array of range filters for the old and new filters (note: most cases will result in empty array)
        if (oldFilter.getRanges().length == 0 && newFilter.getRanges().length > 0)
            newFilter.set('ranges', []);
        else if (oldFilter.getRanges().length > 0 && newFilter.getRanges().length == 0)
            oldFilter.set('ranges', []);
        else
            oldFilter.set('ranges', oldFilter.getRanges().concat(newFilter.getRanges()));
    },

    isExistingMemberByUniqueName : function(memberArray, newMember) {
        // issue 19999: don't push duplicate member if reselecting
        for (var k = 0; k < memberArray.length; k++)
        {
            if (!memberArray[k].hasOwnProperty("uniqueName") || !newMember.hasOwnProperty("uniqueName"))
                continue;

            if (memberArray[k].uniqueName == newMember.uniqueName)
                return true;
        }

        return false;
    },

    /**
     * Returns the set of filters found in newFilters that were not present in oldFilters
     * @param newFilters
     * @param oldFilters
     */
    pruneFilters : function(newFilters, oldFilters) {
        // 15464
        var prunedSelections = [], found;
        for (var s=0; s < newFilters.length; s++) {
            found = false;
            for (var f=0; f < oldFilters.length; f++) {
                if (newFilters[s].isEqual(oldFilters[f])) {
                    found = true;
                }
            }
            if (!found) {
                prunedSelections.push(newFilters[s]);
            }
        }
        return prunedSelections;
    },

    addSelection : function(selections, skipState, merge, clear) {

        var newSelectors = this._getFilterSet(selections);
        var oldSelectors = this.selections;

        /* First check if a clear is requested*/
        if (clear) {
            this.selections = [];
        }

        /* Second Check if a merge is requested */
        if (merge) {
            this.selections = this.mergeFilters(newSelectors, this.selections, oldSelectors);
        }
        else {
            this.selections = newSelectors;
        }

        this.requestSelectionUpdate(skipState, false);
    },

    updateFilterCount : function() {
        this.fireEvent('filtercount', this.filters);
    },

    requestSelectionUpdate : function(skipState, opChange) {

        this.onMDXReady(function(mdx) {

            var sels = [];

            for (var s=0; s < this.selections.length; s++) {
                // construct the query
                sels.push(this.selections[s].getOlapFilter(mdx, this.subjectName));
            }

            if (sels.length == 0) {
                mdx.clearNamedFilter(LABKEY.app.constant.SELECTION_FILTER, function() {
                    if (!skipState)
                        this.updateState();

                    this.fireEvent('selectionchange', this.selections, opChange);
                }, this);
            }
            else {
                mdx.setNamedFilter(LABKEY.app.constant.SELECTION_FILTER, sels, function() {
                    if (!skipState)
                        this.updateState();

                    this.fireEvent('selectionchange', this.selections, opChange);
                }, this);
            }
        }, this);
    },

    moveSelectionToFilter : function() {
        this.addFilters(this.pruneFilters(this.selections, this.filters), false, true);
    },

    getPrivateSelection : function(name) {
        return this.privatefilters[name];
    },

    addPrivateSelection : function(selection, name, callback, scope, memberCaching) {

        this.onMDXReady(function(mdx){

            var filters = [];
            if (Ext.isArray(selection))
            {
                var newSelectors = [];
                for (var s=0; s < selection.length; s++) {

                    if (!selection[s].$className)
                        newSelectors.push(Ext.create(this.getFilterModelName(), selection[s]));
                    else if (selection[s].$className && selection[s].$className == this.getFilterModelName())
                        newSelectors.push(selection[s]);
                }

                this.privatefilters[name] = newSelectors;

                for (s=0; s < newSelectors.length; s++) {
                    filters.push(newSelectors[s].getOlapFilter(mdx, this.subjectName));
                }
            }

            var cb = function() {
                if (Ext.isFunction(callback)) {
                    callback.call(scope || this);
                }
                this.fireEvent('privateselectionchange', mdx._filter[name], name);
            };

            if (Ext.isArray(selection))
            {
                if (memberCaching === false) {
                    mdx.setNamedFilter(name, filters);
                }
                else {
                    mdx.setNamedFilter(name, filters, cb, this);
                }
            }
            else
            {
                // TODO: This is wrong for when working with perspectives
                if (memberCaching === false) {
                    mdx.setNamedFilter(name, [{
                        hierarchy : this.subjectName,
                        membersQuery : selection
                    }]);
                }
                else {
                    mdx.setNamedFilter(name, [{
                        hierarchy : this.subjectName,
                        membersQuery : selection
                    }], cb, this);
                }
            }

        }, this);
    },

    removePrivateSelection : function(name) {
        var me = this;
        this.onMDXReady(function(mdx){

            mdx.setNamedFilter(name, []);
            me.privatefilters[name] = undefined;
            me.fireEvent('privateselectionchange', [], name);

        }, this);
    },

    clearSelections : function(skipState) {
        if (this.selections.length > 0) {
            this.selections = [];
            this.requestSelectionUpdate(skipState, false);
        }
    },

    setSelections : function(selections, skipState) {
        this.addSelection(selections, skipState);
    }
});
