/*
 * Copyright (c) 2012 LabKey Corporation
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

// http://www.sencha.com/forum/archive/index.php/t-145204.html?s=b78df0abfd01c71578f735bbdba8a759
// TODO: Possibly move to Ext Patches if more tree usage appears
Ext4.tree.ViewDropZone.override({

    isValidDropPoint : function(node, position, dragZone, e, data){
        if(!this.callOverridden(arguments))
            return false;

        if(this.view.fireEvent('dragover', node, position, dragZone, this.view.getRecord(node), e, data) === false) {
            return false;
        }

        return true;
    }

});

Ext4.define('LABKEY.ext.panel.FolderManagementPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();
        Ext4.Ajax.timeout = 300000; // 5 minutes.

        Ext4.applyIf(config, {
            layout : 'border',
            draggable : true,
            frame  : false, border : false
        });

        // define Models
        Ext4.define('FolderManagement.Folder', {
            extend : 'Ext.data.Model',
            proxy : {
                type        : 'ajax',
                url         : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api')
            },
            fields : [
                {name : 'containerPath'                  },
                {name : 'id',           type : 'int'     },
                {name : 'isProject',    type : 'boolean' },
                {name : 'text'                           }
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {

        Ext4.applyIf(this, {
            rootId : 1
        });

        this.confirmation = false;

        this.items = [];
        this.items.push(this.initCenterPanel());

        // map of actions available in Folder Management.
        // Value is the action called on the server.
        this.actions = {
            alias  : 'folderAliases',
            create : 'createFolder',
            move   : 'showMoveFolderTree',
            order  : 'reorderFolders',
            remove : 'deleteFolder',
            rename : 'renameFolder',
            reorder: 'reorderFolders'
        };


        // define top toolbar
        if (!this.dockedItems) {
            this.dockedDefaults = true;
            this.dockedItems = [{
                xtype: 'toolbar',
                dock: 'top',
                items: [
                    {text : 'Aliases',              itemId : 'alias',   handler : function() { this.action('alias'); },   scope : this },
                    {text : 'Change Display Order', itemId : 'reorder', handler : function() { this.action('reorder'); }, scope : this },
                    {text : 'Create Subfolder',     itemId : 'create',  handler : function() { this.action('create'); },  scope : this },
                    {text : 'Delete',               itemId : 'remove',  handler : function() { this.action('remove'); },  scope : this },
                    {text : 'Move',                 itemId : 'move',    handler : function() { this.action('move'); },    scope : this },
                    {text : 'Rename',               itemId : 'rename',  handler : function() { this.action('rename'); },  scope : this }
                ]
            }];
        }

        this.callParent([arguments]);
    },

    initCenterPanel : function() {

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false,
            items  : this.getCenterPanelItems(),
            scope  : this
        });

        return Ext4.create('Ext.panel.Panel', {
            border : false, frame : false,
            layout : 'fit',
            region : 'center',
            items  : [this.centerPanel]
        });
    },

    initFolderStore : function() {

        if (this.folderStore)
            return this.folderStore;

        this.folderStore = Ext4.create('Ext.data.TreeStore', {

            model : 'FolderManagement.Folder',
            defaultRootId : this.rootId,
            root : {
                expanded : true,
                text : 'LabKey Server Projects'
            }

        });

        return this.folderStore;
    },

    getCenterPanelItems : function() {

        var treeConfig = {
            store       : this.initFolderStore(),
            cls         : 'folder-management-tree', // used by selenium helper
            rootVisible : true,
            multiSelect : true,
            listeners : {
                selectionchange : function(model, records, eOpts) {
                    this._validateFolders(records);
                },
                scope  : this
            },
            scope : this
        };

        if (this.draggable) {
            treeConfig.viewConfig = {
                plugins: {
                    ptype: 'treeviewdragdrop'
                },
                listeners: {
                    dragover   : this.onNodeDragOver,
                    beforedrop : this.onBeforeNodeDrop,
                    scope : this
                },
                scope : this
            };
        }

        this.treepanel = Ext4.create('Ext.tree.Panel', treeConfig);

        // select the
        this.treepanel.on('load', function(grid, root, success){
            this.ensureVisible(this.selected);
        }, this, {single: true});

        return [this.treepanel];
    },

    action : function(actionType) {
        var selectedFolder = this.treepanel.getSelectionModel().getSelection();

        if (selectedFolder && selectedFolder.length > 0) {
            if (actionType) {
                if (Ext4.isFunction(this.actions[actionType])) {
                    Ext4.defer(this.actions[actionType], 0, this, [selectedFolder]);
                }
                else {
                    actionType = actionType.toLowerCase();
                    if (this.actions[actionType]) {
                        window.location = LABKEY.ActionURL.buildURL('admin', this.actions[actionType], selectedFolder[0].data.containerPath);
                    }
                    else { console.error("'" + actionType + "' is not a valid action."); }
                }
            }
        }
        else {
            console.warn('No folder to selected from which to start the action');
        }
    },

    onNodeDragOver : function(node, dragPos, dragZone, overModel, e, data) {
        for (var i=0; i < data.records.length; i++) {
            var t = this.calculateTarget(data.records[i], overModel, dragPos);
            if (t.msg) {
                dragZone.ddel.update(t.msg);
            }
            if (t.cancel)
                return false;
        }
    },

    /**
     * See beforedrop event on Ext.tree.plugin.TreeViewDragDrop. The following notes only highlight important uses in
     * the context of this component.
     * @param data - more importantly, data.records is an array of records that have been moved. What was "dragged"
     * @param overModel - the record that was targeted. What was being "dropped" on.
     * @param dropPos - determined position of drop. 'before', 'after', and 'append'
     */
    onBeforeNodeDrop : function(htmlNode, data, overModel, dropPos, dropFn, eOpts) {

        var isMove = false;

        function getOrder(dropNodes, targetNode, point) {

            function getOrderHelper(node, target, pt) {

                var order = "", sep = "";

                if (node) {

                    for (var i=0; i < target.parentNode.childNodes.length; i++) {

                        if (target.parentNode.childNodes[i] == node) { continue; }

                        if (target.parentNode.childNodes[i] == target) {
                            if (pt == 'before') {
                                order += sep + node.data.text + ';' + target.data.text;
                            }
                            else if (pt == 'after') {
                                order += sep + target.data.text + ';' + node.data.text;
                            }
                        }
                        else { order += sep + target.parentNode.childNodes[i].data.text; }
                        sep = ';';
                    }
                }

                return order;
            }

            if (dropNodes.length == 1)
                return getOrderHelper(dropNodes[0], targetNode, point);
            else {
                var order = "", sep = "", _order = "", _sep = "";
                var marked = {};

                for (var j=0; j < dropNodes.length; j++){
                    if (dropNodes[j].data.text) {
                        _order += _sep + dropNodes[j].data.text;
                        _sep = ";";
                        marked[dropNodes[j].data.id] = true;
                    }
                }

                var children = targetNode.parentNode.childNodes;
                for (j=0; j < children.length; j++) {
                    if (children[j] == targetNode) {
                        if (point == 'before') {
                            order += sep + _order + ';' + targetNode.data.text;
                        }
                        else if (point == 'after') {
                            order += sep + targetNode.data.text + ';' + _order;
                        }
                    }
                    else if (marked[children[j].data.id]) { continue; }
                    else {
                        order += sep + children[j].data.text;
                    }
                    sep = ';'
                }

                return order;
            }

        }

        // Validate move for each folder to the current target
        for (var i=0; i < data.records.length; i++) {
            var _t = this.calculateTarget(data.records[i], overModel, dropPos);
            if (_t.cancel){
                return false;
            }
            if (i > 0 && data.records[i-1].parentNode.data.id != data.records[i].parentNode.data.id) {
                Ext4.Msg.alert('Note', 'When selecting multiple folders please only select folders that are directly under the same parent folder.');
                return false;
            }
        }

        var s = data.records;
        var target = _t.target;

        if (_t.reorder) {
            this.reorderFolders(s, getOrder(s, target, dropPos), false, null);
        }
        else {

            var d = target;
            var exeSet = [];
            var me = this;

            for (var n=0; n < s.length; n++) {

                exeSet.push(function(f){

                    isMove = true;

                    var _s = s[f];

                    function move() {

                        LABKEY.Security.moveContainer({
                            container : _s.data.containerPath,
                            parent    : target.data.containerPath,
                            success   : function(response) {
                                _s.data.containerPath = response.newPath;
                                me.unmask();

                                successHandler();
                            },
                            failure  : function(response, ops) {
                                var _msg = "Failed to complete move";
                                if (response && response.errors) {
                                    var errors = response.errors;
                                    _msg = "";
                                    for (var i=0; i < errors.length; i++) {
                                        _msg += errors[i].msg + "\n";
                                    }
                                }
                                me.unmask();
                                me.confirmation = false;
                                Ext4.Msg.show({
                                    title : 'Operation Failed',
                                    msg   : _msg,
                                    icon  : Ext4.MessageBox.ERROR,
                                    buttons: Ext4.Msg.OK
                                });
                            },
                            scope : this
                        });
                    }

                    function startMove() {
                        me.mask("Moving Folders. This could take a few minutes...");
                        move();
                    }

                    _s.bubble(function(ps){
                        if (ps.data.isProject){
                            d.bubble(function(pd){
                                if (pd.data.isProject) {

                                    var m = Ext4.MessageBox.buttonText;

                                    m.yes = 'Confirm Move';
                                    m.no  = 'Cancel';

                                    if (me.confirmation) startMove();
                                    else if (ps.data.id == pd.data.id) {

                                        var _t   = 'Move Folder';
                                        var _msg = 'You are moving folder \'' + _s.data.text + '\'. Are you sure you would like to move this folder?';

                                        Ext4.Msg.confirm(_t, _msg, function(btn){
                                            me.confirmation = true;
                                            if (btn == 'yes'){ startMove(); }
                                            else {
                                                // Rollback
                                                me.confirmation = false;
                                                me.treepanel.getStore().load();
                                            }
                                        });
                                    }
                                    else {
                                        Ext4.Msg.confirm('Change Project', 'You are moving folder \'' + _s.data.text + '\' from one project to another. ' +
                                                'This will remove all permissions settings from this folder, any subfolders, and any other configurations. ' +
                                                '<br/><b>This action cannot be undone.</b>',
                                                function(btn){
                                                    me.confirmation = true;
                                                    if (btn == 'yes'){ startMove(); }
                                                    else {
                                                        // Rollback
                                                        me.confirmation = false;
                                                        me.treepanel.getStore().load();
                                                    }
                                                });
                                    }
                                    return false; // stop bubble
                                }
                            });
                            return false; // stop bubble
                        }
                    });

                });
            }

            var c = 0;

            function executeNext() {
                var currentFn = exeSet[c];
                c = c+1;
                if ((c-1) < s.length) {
                    currentFn(c-1);
                }
                else {
                    this.confirmation = false;
                }
            }

            function successHandler(data, response, opts) { Ext4.defer(executeNext, 0, this); }

            Ext4.defer(executeNext, 0, this);
        }
    },

    /**
     * Gives the correct target folder based on the drag/drop action. This should be used by
     * all methods that incorporate drag/drop in folder tree.
     * Returns an object containing the target node and whether to cancel the event:
     * {
     *      target : targetNode,
     *      cancel : true/false,
     *      reorder : true/false
     * }
     * @param node
     * @param target
     * @param point
     */
    calculateTarget : function(node, target, point) {

        if (!target) {
            return {target: undefined, cancel: true};
        }

        if (target.data.containerPath === undefined) {
            return {target: target, cancel: true};
        }

        var ret = {
            target : target,
            cancel : false
        };

        if (point) {
            if (point == 'before' || point == 'after') {

                // check if same parent, check if root node -- cannot elevate to project
                if (target.parentNode && target.parentNode.raw != undefined) {
                    if (node.data.isProject && target.parentNode.data.isProject) {
                        ret.msg = 'Cannot move one Project into another.';
                        return {target: target, cancel: true};
                    }
                    if (target.data.containerPath == undefined) {
                        ret.msg = 'Invalid action';
                        return {target: target, cancel: true};
                    }
                    if (node.data.isProject) {
                        ret.msg = 'Cannot move one Project into another';
                        return {target: target, cancel: true};
                    }
                    if (node.parentNode.data.containerPath == target.parentNode.data.containerPath) {
                        ret.reorder = true;
                        ret.msg = 'Change Display Order';
                    }
                    else
                    {
                        ret.msg = 'Move Folder ' + '/' + node.data.text + ' to ' + target.parentNode.data.containerPath;
                        ret.target = target.parentNode; // dont want to reorder, want to 'move' so focus on parent
                    }
                }
                else {
                    if (node.data.isProject) {
                        ret.msg = 'Change Display Order';
                        ret.reorder = true;
                    }
                    else if (target.data.isProject && !node.data.isProject) {
                        ret.msg = 'Cannot move folder to Project level.';
                        ret.cancel = true;
                    }
                }
            }
            else if (node.data.isProject) {
                // Not allowed to move projects
                ret.msg = 'Cannot move one Project into another';
                ret.cancel = true;
            }
            else if (point == 'append') {
                if (target.data.containerPath == undefined) {
                    ret.msg = 'Invalid action';
                    return {target: target, cancel: true, msg : ret.msg};
                }
                else if (node.parentNode.data.id == target.data.id) {
                    ret.msg = 'A folder ' + node.data.text + ' already exists under ' + target.data.text;
                    return {target: target, cancel: true, msg : ret.msg};
                }
                ret.msg = 'Move Folder ' + '/' + node.data.text + ' to ' + target.data.containerPath;
            }
        }

        return ret;
    },

    _reorder : function(s, order, alpha, alphaNode, successFn) {
        var params = {};
        if (order) {params.order = order;}
        params.resetToAlphabetical = alpha;

        this.mask();
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('admin', 'reorderFoldersApi.api', s[0].data.containerPath),
            method  : 'POST',
            params  : params,
            success : function() {
                if (alpha) {
                    this.treepanel.getStore().load();
                    alphaNode.expand();
                }
                else{
                    if (s[0].data.isProject) {
                        this.treepanel.getStore().load();
                        // TODO: Expand the root node
                    }
                }
                this.confirmation = false;
                this.unmask();
                if (successFn) { successFn(); }
            },
            failure : function() {
                this.confirmation = false;
                this.unmask();
                Ext4.Msg.alert('Error Reordering', 'Failed to Reorder.');
                this.treepanel.getStore().load();
            },
            scope : this
        });
    },

    reorderFolders : function(s, order, alpha, alphaNode, successFn) {

        if (this.confirmation) {
            this._reorder(s, order, alpha, alphaNode, successFn);
        }
        else {
            this.confirmation = true;
            Ext4.Msg.confirm('Change Display Order', 'Are you sure you want to change the folder display order?', function(btn, text, z){
                if (btn == 'yes') { this._reorder(s, order, alpha, alphaNode, successFn); }
                else {
                    this.confirmation = false;
                    this.treepanel.getStore().load();
                }
            }, this);
        }
    },

    ensureVisible : function(id) {
        var target = this.treepanel.getView().getTreeStore().getRootNode().findChild('id', id, true);
        if (target)
        {
            this.treepanel.selectPath(target.getPath());
        }
    },

    _validateFolders : function(nodes) {
        var tool = this.getDockedItems('toolbar');
        if (this.dockedDefaults && tool && tool[0]) {
            if (nodes.length == 1)
                tool[0].getComponent('move').setDisabled(nodes[0].data.isProject);

            var isDisabled = (nodes.length > 1);
            tool[0].getComponent('alias').setDisabled(isDisabled);
            tool[0].getComponent('reorder').setDisabled(isDisabled);
            tool[0].getComponent('create').setDisabled(isDisabled);
            tool[0].getComponent('remove').setDisabled(isDisabled);
            tool[0].getComponent('rename').setDisabled(isDisabled);
        }
    },

    mask : function(msg) {
        this.getEl().mask(msg || 'Loading...');
    },

    unmask : function() {
        this.getEl().unmask();
    }
});