/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.Browser', {
    extend: 'Ext.panel.Panel',

    layout: 'border',

    useHistory: false,

    qdpPrefix: 'qdp-', // query details
    sspPrefix: 'ssp-', // schema summary
    historyPrefix: 'sbh-',

    cls: 'schemabrowser',

    constructor : function(config) {
        this.callParent([config]);
        this.addEvents('schemasloaded', 'selectschema', 'selectquery');
    },

    initComponent : function() {

        this.tabFactory = LABKEY.query.browser.SchemaBrowserTabFactory;

        this.items = [{
            itemId: 'lk-sb-tree',
            xtype: 'qbrowser-tree',
            region: 'west',
            split: true,
            flex: 1,
            minWidth: 200,
            autoScroll: true,
            enableDrag: false,
            useArrows: true,
            listeners: {
                select : this.onTreeClick,
                schemasloaded : function() {
                    this.fireEvent('schemasloaded', this);
                },
                scope: this
            }
        },{
            itemId: 'lk-sb-details',
            xtype: 'tabpanel',
            region: 'center',
            activeTab: 0,
            flex: 5,
            items: [{
                itemId: 'lk-sb-panel-home',
                xtype: 'qbrowser-view-home',
                listeners: {
                    schemaclick: function(schemaName) {
                        this.selectSchema(schemaName);
                        this.showPanel(this.sspPrefix + schemaName);
                    },
                    scope: this
                }
            }],
            enableTabScroll: true,
            defaults: { autoScroll:true, border: false },
            border: false,
            listeners: {
                tabchange: this.onTabChange,
                scope: this
            }
        }];

        this._initTbar();

        this.callParent();

        if (this.useHistory) {
            this._initHistory();
        }

        this._initResize();

        this.selectQueryTask = new Ext4.util.DelayedTask(this._selectQueryTaskHandler, this);
        this.on('selectquery', this.onSelectQuery, this);
        this.on('schemasloaded', this._bindURL, this, {single: true});
    },

    /**
     * @private
     * Binds to URL parameters as well as the # history.
     */
    _bindURL : function() {
        var params = LABKEY.ActionURL.getParameters();

        var schemaName = params.schemaName;
        var queryName = params.queryName || params['query.queryName'];

        if (!Ext4.isEmpty(schemaName)) {
            if (!Ext4.isEmpty(queryName)) {
                this.selectQuery(schemaName, queryName);
            }
            else {
                this.selectSchema(schemaName, queryName);
            }
        }

        if (window.location.hash && window.location.hash.length > 1) {
            // window.location.hash returns an decoded value, which
            // is different from what Ext4.History.getToken() returns
            // so use the same technique Ext does for getting the hash
            var href = top.location.href,
                idx = href.indexOf('#'),
                hash = idx >= 0 ? href.substr(idx + 1) : null;

            if (hash) {
                this.onHistoryChange(hash);
            }
        }
    },

    _initHistory : function() {
        Ext4.History.on('change', this.onHistoryChange, this);
    },

    _initResize : function() {
        var resize = function(w, h) {
            LABKEY.ext4.Util.resizeToViewport(this, w, h, 46, 32);
        };

        Ext4.EventManager.onWindowResize(resize, this);

        this.on('afterrender', function() {
            Ext4.defer(function() {
                var size = Ext4.getBody().getBox();
                resize.call(this, size.width, size.height);
            }, 300, this);
        });
    },

    _initTbar : function() {
        var tbar = [{
            text: 'Refresh',
            handler: this.onRefresh,
            tooltip: 'Refreshes the tree of schemas and queries, or a particular schema if one is selected.',
            renderTpl: [
            '<span id="{id}-btnEl" class="iconbtn">',
                '<span class="fa fa-refresh"></span>',
                '<span id="{id}-btnInnerEl" class="labkey-fa-icon-text">',
                    '{text:htmlEncode}',
                '</span>',
            '</span>'
            ],
            scope: this
        },{
            text: 'Validate Queries',
            handler: function() { this.showPanel('lk-vq-panel'); },
            tooltip: 'Opens the validate queries tab where you can validate all the queries defined in this folder.',
            renderTpl: [
            '<span id="{id}-btnEl" class="iconbtn">',
                '<span class="fa fa-check-circle"></span>',
                '<span id="{id}-btnInnerEl" class="labkey-fa-icon-text">',
                    '{text:htmlEncode}',
                '</span>',
            '</span>'
            ],
            scope: this
        }];

        if (LABKEY.Security.currentUser.isAdmin) {
            tbar.push({
                text: 'Schema Administration',
                handler: function() {
                    window.location = LABKEY.ActionURL.buildURL('query', 'admin');
                },
                tooltip: 'Create or modify external schemas.',
                renderTpl: [
                    '<span id="{id}-btnEl" class="iconbtn">',
                        '<span class="fa-stack fa-1x labkey-fa-stacked-wrapper">',
                            '<span class="fa fa-folder-o fa-stack-2x"></span>',
                            '<span class="fa fa-stack-1x fa-plus-circle labkey-fa-plus-circle-folder"></span>',
                        '</span>',
                        '<span id="{id}-btnInnerEl" class="labkey-fa-icon-text">',
                            '{text:htmlEncode}',
                        '</span>',
                    '</span>'
                ],
                scope: this
            });
            tbar.push({
                text: 'Create New Query',
                handler: this.onCreateQueryClick,
                tooltip: 'Create a new query in the selected schema (requires that you select a particular schema or query within that schema).',
                renderTpl: [
                    '<span id="{id}-btnEl" class="iconbtn">',
                        '<span class="fa-stack fa-1x labkey-fa-stacked-wrapper">',
                            '<span class="fa fa-folder-o fa-stack-2x"></span>',
                            '<span class="fa fa-stack-1x fa-plus-circle labkey-fa-plus-circle-folder"></span>',
                        '</span>',
                        '<span id="{id}-btnInnerEl" class="labkey-fa-icon-text">',
                            '{text:htmlEncode}',
                        '</span>',
                    '</span>'
                ],
                scope: this
            });
            tbar.push({
                text: 'Manage Remote Connections',
                handler: function() {
                    window.location = LABKEY.ActionURL.buildURL('query', 'manageRemoteConnections');
                },
                tooltip: 'Manage remote connection credentials for remote LabKey server authentication.',
                renderTpl: [
                    '<span id="{id}-btnEl" class="iconbtn">',
                        '<span class="fa-stack fa-1x labkey-fa-stacked-wrapper">',
                            '<span class="fa fa-folder-o fa-stack-2x"></span>',
                            '<span class="fa fa-stack-1x fa-plus-circle labkey-fa-plus-circle-folder"></span>',
                        '</span>',
                        '<span id="{id}-btnInnerEl" class="labkey-fa-icon-text">',
                            '{text:htmlEncode}',
                        '</span>',
                    '</span>'
                ],
                scope: this
            });
            tbar.push({
                text: 'Generate Schema Export',
                handler: function() {
                    location.href = LABKEY.ActionURL.buildURL('query', 'generateSchema', null, {returnUrl: window.location});
                },
                tooltip: 'Generate schema export sql script for migrating a schema.',
                renderTpl: [
                    '<span id="{id}-btnEl" class="iconbtn">',
                        '<span class="fa-stack fa-1x labkey-fa-stacked-wrapper">',
                            '<span class="fa fa-folder-o fa-stack-2x"></span>',
                            '<span class="fa fa-stack-1x fa-plus-circle labkey-fa-plus-circle-folder"></span>',
                        '</span>',
                        '<span id="{id}-btnInnerEl" class="labkey-fa-icon-text">',
                            '{text:htmlEncode}',
                        '</span>',
                    '</span>'
                ],
                scope: this
            });
        }

        this.tbar = tbar;
    },

    _selectQueryTaskHandler : function() {
        var schemaName = this.activeSchema;
        var queryName = this.activeQuery;
        this.expandSchema(schemaName, function (success, schemaNode) {
            if (success) {
                var tree = this.getTree(),
                    queryNode; // look for the query node under both built-in and user-defined

                var comparison = function(n) {
                    if (n.data.queryName.toLowerCase() === queryName.toLowerCase()) {
                        queryNode = n;
                    }
                };

                // TODO: check Issue 15674: if more than 100 queries are present, we include a placeholder node saying 'More..', which lacks queryName
                if (schemaNode.length > 0) {
                    Ext4.each(schemaNode[0].childNodes, comparison);
                }
                if (!queryNode && schemaNode.length > 1) {
                    Ext4.each(schemaNode[1].childNodes, comparison);
                }

                if (!queryNode) {
                    // TODO: consider case of issue below...
                    //Issue 15674: if there are more than 100 queries, some queries will not appear in the list.  therefore this is a legitimate case
                    return;
                }

                tree.getSelectionModel().select(queryNode);
                this.fireEvent('selectquery', schemaName, queryName);
            }
        }, this);
    },

    buildQueryPanelId : function(schemaName, queryName) {
        return this.qdpPrefix + encodeURIComponent('&' + schemaName.toString() + '&' + queryName);
    },

    getTree : function() {
        return this.getComponent('lk-sb-tree');
    },

    expandSchema : function(schemaName, callback, scope) {
        if (!(schemaName instanceof LABKEY.SchemaKey)) {
            schemaName = LABKEY.SchemaKey.fromString(schemaName);
        }

        var tree = this.getTree(),
            schemaNode = this.getSchemaNode(tree, schemaName);

        if (schemaNode) {
            schemaNode.expand(false, function (schemaNode) {
                if (Ext4.isFunction(callback)) {
                    callback.call((scope || this), true, schemaNode);
                }
            });
        }
        else {
            // Attempt to expand along the schemaName path.
            // Find the data source root node first
            var parts = schemaName.getParts(),
                root = tree.getRootNode(),
                dataSourceNodes = root.childNodes,
                dataSourceRoot, node, part, child, i;

            for (i = 0; i < dataSourceNodes.length; i++) {
                node = dataSourceNodes[i];
                part = parts[0].toLowerCase();
                child = node.findChildBy(function (n) {
                    return n.data.name && n.data.name.toLowerCase() == part;
                });

                if (child) {
                    dataSourceRoot = node;
                    break;
                }
            }

            // Expand along the patch to fetch the schema data
            if (dataSourceRoot) {
                var partIndex = 0;
                var schemaPathStr = '/root/' + dataSourceRoot.data.name + '/' + parts[partIndex];
                var onExpand = function (success, lastNode) {
                    if (!success) {
                        Ext4.Msg.alert("Missing Schema", "The schema '" + Ext4.htmlEncode(schemaName.toDisplayString()) + "' was not found. The data source for the schema may be unreachable, or the schema may have been deleted.");
                        if (Ext4.isFunction(callback)) {
                            callback.call((scope || this), true, lastNode);
                        }
                    }

                    // Might need to recurse to expand child schemas
                    if (!success || ++partIndex === parts.length) {
                        lastNode.expand(false, function (lastNode) {
                            if (Ext4.isFunction(callback)) {
                                callback.call((scope || this), true, lastNode);
                            }
                        });
                    }
                    else {
                        schemaPathStr += '/' + parts[partIndex];
                        tree.expandPath(schemaPathStr, 'name', '/', onExpand);
                    }
                };
                tree.expandPath(schemaPathStr, 'name', '/', onExpand);
            }
            else {
                Ext4.Msg.alert('Missing Schema', "The schema '" + Ext4.htmlEncode(schemaName.toDisplayString()) + "' was not found. The data source for the schema may be unreachable, or the schema may have been deleted.");
            }
        }
    },

    getCreateQueryUrl : function(schemaName, queryName) {
        var params = {schemaName: schemaName.toString()};
        if (queryName) {
            params.ff_baseTableName = queryName;
        }

        return LABKEY.ActionURL.buildURL('query', 'newQuery', undefined, params);
    },

    getSchemaNode : function(tree, schemaName) {
        if (!(schemaName instanceof LABKEY.SchemaKey)) {
            schemaName = LABKEY.SchemaKey.fromString(schemaName);
        }

        var parts = schemaName.getParts(),
            root = tree.getRootNode(),
            node, part,
            dataSourceNodes = root.childNodes, i, j;

        for (i = 0; i < dataSourceNodes.length; i++) {
            node = dataSourceNodes[i];
            for (j = 0; node && j < parts.length; j++) {
                part = parts[j].toLowerCase();
                node = node.findChildBy(function(n) {
                    return n.data.name && n.data.name.toLowerCase() == part;
                });
            }

            // found node
            if (node && j === parts.length) {
                return node;
            }
        }

        return null;
    },

    onCreateQueryClick : function() {
        //determine which schema is selected in the tree
        var selection = this.getTree().getSelectionModel().getSelection();

        if (!Ext4.isEmpty(selection) && selection[0].data.schemaName) {
            window.location = this.getCreateQueryUrl(selection[0].data.schemaName, selection[0].data.queryName);
        }
        else {
            Ext4.Msg.alert('Which Schema?', 'Please select the schema in which you want to create the new query.');
        }
    },

    onHistoryChange : function(token) {
        if (!Ext4.isEmpty(token)) {
            token = token.substring(this.historyPrefix.length);
        }
        else {
            token = 'lk-sp-panel-home';
        }

        if (this.qdpPrefix === token.substring(0, this.qdpPrefix.length)) {
            var idMap = this.parseQueryPanelId(token);
            token = this.buildQueryPanelId(idMap.schemaName, idMap.queryName);
        }

        this.showPanel(token);
    },

    onLookupClick : function(schemaName, queryName, containerPath) {
        if (containerPath && containerPath !== LABKEY.ActionURL.getContainer()) {
            var url = LABKEY.ActionURL.buildURL('query', 'begin', containerPath, {schemaName: schemaName, queryName: queryName});
            window.open(url);
        }
        else {
            this.selectQuery(schemaName, queryName);
        }
    },

    onRefresh : function() {
        // clear the query details cache
        LABKEY.query.browser.cache.Query.clearAll();
        LABKEY.query.browser.cache.QueryDetails.clearAll();

        // remove all tabs except for the first one (home)
        var tabs = this.getComponent('lk-sb-details');
        while (tabs.items.length > 1) {
            tabs.remove(tabs.items.length - 1, true);
        }

        // reload schema tree
        this.getTree().getRootNode().reload();
    },

    onSelectQuery : function(schemaName, queryName) {
        this.showQueryDetails(schemaName, queryName);
    },

    onTabChange : function(tabpanel, tab) {
        if (tab.schemaName && tab.queryName) {
            this.selectQuery(tab.schemaName, tab.queryName);
        }
        else if (tab.schemaName) {
            this.selectSchema(tab.schemaName);
        }

        if (!tab.itemId) {
            console.error('tab configued incorrectly:', tab);
        }

        try {
            decodeURIComponent(tab.itemId);
            Ext4.History.add(this.historyPrefix + tab.itemId);
        }
        catch (err) {
            console.log('Unable to update history. URI malformed:', tab.itemId);
        }
    },

    onTreeClick : function(node, record) {
        var schemaName = record.get('schemaName');
        if (schemaName && !record.get('queryName')) {
            this.showPanel(this.sspPrefix + schemaName);
        }
        else if (record.get('leaf')) {
            this.showQueryDetails(schemaName, record.get('queryName'));
        }
    },

    parseQueryPanelId : function(token) {
        var id = token;
        if (id.indexOf(this.qdpPrefix) > -1) {
            id = id.substring(this.qdpPrefix.length);
        }

        // onHistoryChange hands us a URI encoded token in Chrome and a decoded URI token in Firefox
        var ret = {};
        if (id.indexOf('&') === 0) {
            // strip off leading '&'
            id = id.substring(1);
        }
        else if (id.indexOf('%26') === 0) {
            // decode and strip off leading '&' encoded
            id = decodeURIComponent(id.substring('%26'.length));
        }
        else {
            console.warn('Expected to find panel id of the form \'&schemaName&queryName\': ', id);
            return ret;
        }

        var amp = id.indexOf('&');
        if (amp > -1) {
            var schemaName = id.substring(0, amp);
            ret.schemaName = LABKEY.SchemaKey.fromString(schemaName);
            ret.queryName = id.substring(amp+1);
        }

        return ret;
    },

    /**
     * This can be used to select a given queryName. A corresponding 'selectquery' event will fire
     * once a query is actively selected.
     * @param schemaName
     * @param queryName
     */
    selectQuery : function(schemaName, queryName) {
        this.activeSchema = schemaName;
        this.activeQuery = queryName;
        this.selectQueryTask.delay(100);
    },

    selectSchema : function(schemaName) {
        this.expandSchema(schemaName, function(success, schemaNode) {
            if (success === true) {
                if (Ext4.isArray(schemaNode)) {
                    if (!Ext4.isEmpty(schemaNode)) {
                        schemaNode = schemaNode[0];
                    }
                }

                if (Ext4.isObject(schemaNode)) {
                    this.getTree().getSelectionModel().select(schemaNode.parentNode);
                }
            }
        }, this);
    },

    showPanel : function(queryId) {
        var tabs = this.getComponent('lk-sb-details');
        //var safeId = this.getSafeId(queryId);
        var tab = tabs.getComponent(queryId);
        if (tab) {
            tabs.setActiveTab(tab);
        }
        else {
            var panel = this.tabFactory.generateTab(queryId, this);
            if (panel) {
                tabs.add(panel);
                tabs.setActiveTab(panel);
            }
        }
    },

    showQueryDetails : function(schemaName, queryName) {
        this.showPanel(this.buildQueryPanelId(schemaName, queryName));
    }
});

Ext4.define('LABKEY.query.browser.SchemaBrowserTabFactory', {
    singleton: true,

    generateTab : function(tabId, browser) {
        var panel;
        if (tabId === 'lk-vq-panel') {
            panel = Ext4.create('LABKEY.query.browser.view.Validate', {
                itemId: tabId,
                closable: true,
                title: 'Validate Queries',
                cls: 'qbrowser-validate', // tests
                listeners: {
                    queryclick: browser.onLookupClick,
                    scope: browser
                }
            });
        }
        else if (browser.qdpPrefix === tabId.substring(0, browser.qdpPrefix.length)) {
            var idMap = browser.parseQueryPanelId.call(browser, tabId);
            panel = Ext4.create('LABKEY.query.browser.view.QueryDetails', {
                itemId: tabId,
                schemaName: idMap.schemaName,
                queryName: idMap.queryName,
                title: Ext4.htmlEncode(idMap.schemaName.toDisplayString()) + '.' + Ext4.htmlEncode(idMap.queryName),
                autoScroll: true,
                listeners: {
                    lookupclick: browser.onLookupClick,
                    scope: browser
                },
                closable: true
            });
        }
        else if (browser.sspPrefix === tabId.substring(0, browser.sspPrefix.length)) {
            var schemaName = tabId.substring(browser.sspPrefix.length),
                    schemaPath = LABKEY.SchemaKey.fromString(schemaName);

            panel = Ext4.create('LABKEY.query.browser.view.SchemaDetails', {
                itemId: tabId,
                schemaName: schemaPath,
                schemaBrowser: this,
                title: Ext4.htmlEncode(schemaPath.toDisplayString()),
                autoScroll: true,
                listeners: {
                    queryclick: browser.showQueryDetails,
                    scope: browser
                },
                closable: true
            });
        }

        return panel;
    }
});