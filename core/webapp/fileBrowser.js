var TREESELECTION_EVENTS =
{
    selectionchange:"selectionchange",
    beforeselect:"beforeselect"
};

var GRIDPANEL_EVENTS =
{
    click:"click",
    dblclick:"dblclick",
    contextmenu:"contextmenu",
    mousedown:"mousedown",
    mouseup:"mouseup",
    mouseover:"mouseover",
    mouseout:"mouseout",
    keypress:"keypress",
    keydown:"keydown",
    cellmousedown:"cellmousedown",
    rowmousedown:"rowmousedown",
    headermousedown:"headermousedown",
    cellclick:"cellclick",
    celldblclick:"celldblclick",
    rowclick:"rowclick",
    rowdblclick:"rowdblclick",
    headerclick:"headerclick",
    headerdblclick:"headerdblclick",
    rowcontextmenu:"rowcontextmenu",
    cellcontextmenu:"cellcontextmenu",
    headercontextmenu:"headercontextmenu",
    bodyscroll:"bodyscroll",
    columnresize:"columnresize",
    columnmove:"columnmove",
    sortchange:"sortchange"
};

var ROWSELECTION_MODEL =
{
    selectionchange:"selectionchange",
    beforerowselect:"beforerowselect",
    rowselect:"rowselect",
    rowdeselect:"rowdeselect"
};


var h = Ext.util.Format.htmlEncode;
function log(o)
{
    if ("console" in window)
        console.log(o);
}


function renderIcon(value, metadata, record, rowIndex, colIndex, store)
{
    if (!value)
    {
        var file = record.get("file");
        if (!file)
        {
            value = FileBrowser.prototype.FOLDER_ICON;
        }
        else
        {
            var name = record.get("name");
            var i = name.lastIndexOf(".");
            var ext = i >= 0 ? name.substring(i) : name;
            value = LABKEY.contextPath + "/project/icon.view?name=" + ext;
        }
    }
    return "<img width=16 height=16 src='" + value + "'>";
}


function renderFileSize(value, metadata, record, rowIndex, colIndex, store)
{
    if (!record.get('file')) return "";
    var f =  Ext.util.Format.fileSize(value);
    return "<span title='" + f + "'>" + value + "</span>";
}

var _rDateTime = Ext.util.Format.dateRenderer("Y-m-d H:i:s");
var _longDateTime = Ext.util.Format.dateRenderer("l, F d, Y g:i:s A");
function renderDateTime(value, metadata, record, rowIndex, colIndex, store)
{
    if (!value) return "";
    if (value.getTime() == 0) return "";
    return "<span title='" + _longDateTime(value) + "'>" + _rDateTime(value) + "<span>";
    //return _rDateTime(value, metadata, record, rowIndex, colIndex, store);
}


//
// FileSystem
//

// FileRecord should look like
//      path (string), name (string), file (bool), created (date), modified (date), size (int), iconHref(string)

var FILESYSTEM_EVENTS = {listfiles:"listfiles", history:"history"};

var FileSystem = function(config)
{
    this.directoryMap = {}; //  map<path,(time,[records])>
    this.addEvents(FILESYSTEM_EVENTS.listfiles);
    this.addEvents(FILESYSTEM_EVENTS.history);
};

Ext.extend(FileSystem, Ext.util.Observable,
{
    rootPath : "/",
    pathSeparator : "/",
    
    // causes directory listFiles to be fired
    listFiles : function(path, callback)    // callback(filesystem, success, path, records)
    {
        if (path in this.directoryMap)
        {
            if (typeof callback == "function")
                callback.defer(1, null, [this, true, path, this.directoryMap[path]]);
        }
        else
        {
            var ok = this.reloadFiles(path, callback);
            if (!ok && typeof callback == "function")
                callback(this, ok, path, []);
        }
    },

    // causes directory listFiles to be fired, forces refresh from underlying store
    // return false on immediate fail
    reloadFiles : function(path, callback)
    {
    },

    // causes "history" event to be fired
    getHistory : function(path, callback)
    {
        this.fireEvent(FILESYSTEM_EVENTS.history, []);
    },

    // protected

    _addFiles : function(path, records)
    {
        this.directoryMap[path] = records;
        this.fireEvent(FILESYSTEM_EVENTS.listfiles, path, records);
    },


    _recordFromCache : function(path)
    {
        if (!path || path == this.rootPath)
            // UNDONE this.rootRecord
            return new this.FileRecord({id:this.rootPath, path:this.rootPath, name:this.rootPath, file:false}, this.rootPath);
        var parent = this.parentPath(path) || this.rootPath;
        var files = this.directoryMap[parent];
        if (!files && parent.charAt(parent.length-1) == this.pathSeparator)
            parent = parent.substring(0,parent.length-1);
        var files = this.directoryMap[parent];
        if (!files)
            return null;
        for (var i=0 ; i<files.length ; i++)
        {
            var r = files[i];
            if (r.data.path == path)
                return r;
        }
        return null;
    },

    
    // util
    
    concatPaths : function(a,b)
    {
        var c = 0;
        if (a.length > 0 && a.charAt(a.length-1)==this.pathSeparator) c++;
        if (b.length > 0 && b.charAt(0)==this.pathSeparator) c++;
        if (c == 0)
            return a + this.pathSeparator + b;
        else if (c == 1)
            return a + b;
        else
            return a + b.substring(1);
    },

    parentPath : function(p)
    {
        if (!p)
            p = this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.pathSeparator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.pathSeparator);
        if (i > -1)
            p = p.substring(0,i+1);
        return p;
    },

    fileName : function(p)
    {
        if (!p || p == this.rootPath)
            return this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.pathSeparator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.pathSeparator);
        if (i > -1)
            p = p.substring(i+1);
        return p;
    }
});



//
// WebdavFileSystem
//


// config
// baseUrl: root of the webdav tree (http://localhost:8080/labkey/_webdav)
// rootPath: root of the tree we want to browse e.g. /home/@pipeline/

var WebdavFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {
        baseUrl: LABKEY.contextPath + "/_webdav",
        rootPath: "/"
    });
    this.prefixUrl = this.concatPaths(this.baseUrl, this.rootPath);
    WebdavFileSystem.superclass.constructor.call(this);

    var baseUrl = this.baseUrl;
    this.FileRecord = Ext.data.Record.create(
        [
            {name: 'path', mapping: 'href',
                convert : function (v, rec)
                {
                    return baseUrl ? v.replace(baseUrl, "") : v;
                }
            },
            {name: 'href', mapping: 'href'},
            {name: 'name', mapping: 'propstat/prop/displayname'},
            {name: 'file', mapping: 'href', type: 'boolean',
                convert : function (v, rec)
                {
                    return v.length > 0 && v.charAt(v.length-1) != '/';
                }
            },
            {name: 'created', mapping: 'propstat/prop/creationdate', type: 'date', dateFormat : "c"},
            {name: 'modified', mapping: 'propstat/prop/getlastmodified', type: 'date'},
            {name: 'modifiedby', mapping: 'propstat/prop/modifiedby'},
            {name: 'size', mapping: 'propstat/prop/getcontentlength', type: 'int'},
            {name: 'iconHref'}
        ]);
    this.connection = new Ext.data.Connection({method: "GET", headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.reader = new Ext.data.XmlReader({record : "response", id : "href"}, this.FileRecord);
};

Ext.extend(WebdavFileSystem, FileSystem,
{
    reloadFiles : function(path, callback)
    {
        var url = this.concatPaths(this.prefixUrl, path);
        this.connection.url = url;

        var args = {url: url, path: path, callback:callback};
        //load(params, reader, callback, scope, arg)
        this.proxy.load({method:"PROPFIND",depth:"1,noroot"}, this.reader, this.processFiles, this, args);
        return true;
    },

    processFiles : function(result, args, success)
    {
        var path = args.path;
        var callback = args.callback;
        var records = [];
        if (success)
        {
            records = result.records;
            this._addFiles(path, records);
        }
        if (typeof callback == "function")
            callback(this, success, path, records);
    }
});


//
// AppletFileSystem
//

// getDropApplet=function()
var AppletFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {path:"/"});
    WebdavFileSystem.superclass.constructor.call(this);
    this.FileRecord = Ext.data.Record.create(['path', 'name', 'file', 'created', 'modified', 'modifiedby', 'size', 'iconHref']);
    this.connection = new Ext.data.Connection({method: "GET", headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.reader = new Ext.data.XmlReader({record : "response", id : "href"}, this.FileRecord);
};

Ext.extend(AppletFileSystem, FileSystem,
{
    pathSeparator : "\\",    
    retry : 0,
    
    reloadFiles : function(directory, callback)
    {
        var applet = this.getDropApplet();
        if (!applet)
        {
            this.retry++;
            this.reloadFiles.defer(100, this, [directory, callback]);
            return true;
        }
        this.retry = 0;
        if (!directory)
            return false;
        var root = directory == "/";
        if (root)
        {
            if (!applet.local_listRoots())
                return false;
        }
        if (!root || 1==applet.local_getFileCount())
        {
            if (!applet.local_changeDirectory(directory))
                return false;
        }
        var count = applet.local_getFileCount();
        var records = [];
        for (var i=0 ; i<count ; i++)
        {
            var name = applet.local_getName(i);
            if (name.charAt(name.length-1) == '\\')
                name = name.substring(0,name.length-1);
            var file = !applet.local_isDirectory(i);
            var path = applet.local_getPath(i); 
            var ts = applet.local_getTimestamp(i); 
            var lastModified = ts ? new Date(ts) : null;
            var size = applet.local_getSize(i);
            var data = {id:path, name:name, path:path, file:file, modified:lastModified, size:size, iconHref:file?null:this.FOLDER_ICON, modifiedby:null};
            records.push(new this.FileRecord(data, path));
        }
        this._addFiles(directory, records);
        if (typeof callback == "function")
            callback.defer(1, null, [this, true, directory, records]);
        return true;
    }
});


//
// FileSystemTreeLoader
//

var FileSystemTreeLoader = function (config)
{
    Ext.apply(this, config);
    this.addEvents("beforeload", "load", "loadexception");
    FileSystemTreeLoader.superclass.constructor.call(this);
};

Ext.extend(FileSystemTreeLoader, Ext.tree.TreeLoader,
{
    fileFilter : null,
    displayFiles: true,
    url: true, // hack for Ext.tree.TreeLoader.load()

    requestData : function(node, callback)
    {
        if (this.fireEvent("beforeload", this, node, callback) !== false)
        {
            var args = {node:node, callback:callback};
            if (this.fileSystem.listFiles(node.id, this.listCallback.createDelegate(this, [args], true)))
                return true;
        }
        if (typeof callback == "function")
            callback();
    },

    createNode : function (data)
    {
        if (data.file)
        {
            if (!this.displayFiles)
                return null;
            if (this.fileFilter)
                data.disabled = !this.fileFilter.test(data.text);
        }
        var n = Ext.apply({},{id:data.id || data.path, leaf:data.file, text:data.name, href:null}, data);
        return FileSystemTreeLoader.superclass.createNode.call(this, n);
    },

    createNodeFromRecord : function(record)
    {
        var n = this.createNode(record.data);
        if (n)
            n.record = record;
        return n;
    },

    listCallback : function (filesystem, success, path, records, args)
    {
        if (!success)
        {
            if (typeof args.callback == "function")
                args.callback();
            return;
        }
        try
        {
            var node = args.node;
            node.beginUpdate();
            for (var i = 0; i < records.length; i++)
            {
                var record = records[i];
                var n = this.createNodeFromRecord(record);
                if (n)
                    node.appendChild(n);
            }
            node.endUpdate();
            if (typeof args.callback == "function")
                args.callback(this, node);
        }
        catch (e)
        {
//          UNDONE:
//          this.handleFailure(response);
            window.alert(path + " " + e);
        }
    }
});




//
//  FILE BROWSER UI
//
// UNDONE: convert to a proper 'class'


var BROWSER_EVENTS = {selectionchange:"selectionchange", directorychange:"directorychange"};

var FileBrowser = function(config)
{
    this.addEvents( [ BROWSER_EVENTS.selectionchange, BROWSER_EVENTS.directorychange ]);
    this.init(config);
};
Ext.extend(FileBrowser, Ext.util.Observable,
{
    FOLDER_ICON: LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/tree/folder.gif",

    // instance/config variables
    showFolders: true,
    showAddressBar: true,
    showDetails: true,
    showProperties: true,

    url: "/labkey/_webdav",
    grid: null,
    store: null,

    currentDirectory: null,
    selectedRecord: null,

    //
    // actions
    //

    action : new Ext.Action({text: 'Alert', iconCls: 'blist', scope: this, handler: function()
    {
        window.alert('Click','You clicked on "Action 1".');
    }}),

    getDownloadAction : function()
    {
        return new Ext.Action({text: 'Download', scope: this, handler: function()
            {
                if (this.selectedRecord && this.selectedRecord.data.file)
                    window.location = this.url + p + "?contentDisposition=attachment";
            }});
    },

    getParentFolderAction : function()
    {
        return new Ext.Action({text: 'Up', scope: this, handler: function()
        {
            // CONSIDER: PROPFIND to ensure this link is still good?
            var p = this.currentDirectory.data.path;
            var dir = this.fileSystem.parentPath(p) || this.fileSystem.rootPath;
            var record = this.fileSystem._recordFromCache(dir);
            if (record)
                this.changeDirectory(record);
        }});
    },

    getRefreshAction : function()
    {
        return new Ext.Action({text: 'Refresh', scope:this, handler: function()
        {
            if (!this.currentDirectory)
                return;
            this.fileSystem.reloadFiles(this.currentDirectory.data.path, function(filesystem, success, path, records)
            {
                this.store.removeAll();
                this.store.add(records);
            }.createDelegate(this));
//            this.tree.getRootNode().reload();
            var sel = this.tree.getSelectionModel().getSelectedNode();
            if (sel)
                sel.reload();
        }});
    },

    changeDirectory : function(record)
    {
        if (typeof record == "string")
        {
            record = this.fileSystem._recordFromCache(record);
            if (!record)
                return;
        }
        if (!this.currentDirectory || this.currentDirectory.data.path != record.data.path)
        {
            this.currentDirectory = record;
            this.fireEvent(BROWSER_EVENTS.directorychange, record);
        }
    },

    selectRecord : function(record)
    {
        if (typeof record == "string")
        {
            record = this.fileSystem._recordFromCache(record);
            if (!record)
                return;
        }
        var path = record.data.path;
        if (!this.selectedRecord || this.selectedRecord.data.path != path)
        {
            this.selectedRecord = record;
            this.fireEvent(BROWSER_EVENTS.selectionchange, record);
        }
    },


    //
    // event handlers
    //
    Grid_onRowselect : function(sm, rowIdx, record)
    {
        if (this.tree)
            this.tree.getSelectionModel().clearSelections();
        if (record)
            this.selectRecord(record);
    },

    Grid_onCelldblclick : function(grid, rowIndex, columnIndex, e)
    {
        var record = grid.getStore().getAt(rowIndex);
        this.selectRecord(record);
        if (!record.data.file)
        {
            this.changeDirectory(record);
            if (this.tree)
            {
                var treePath = this.treePathFromId(record.id);
                this.tree.expandPath(treePath);
                var node = this.tree.getNodeById(record.id);
                if (node)
                {
                    node.ensureVisible();
                    node.select();
                }
            }
        }
    },

    Tree_onSelectionchange : function(sm, node)
    {
        if (this.grid)
            this.grid.getSelectionModel().clearSelections();
        if (node)
        {
            this.selectRecord(node.record);
            this.changeDirectory(node.record);
        }
    },

    generateAddressBar : function(path)
    {
        var el = Ext.get('addressBar');
        if (!el)
            return;
        el.update(h(unescape(path)));
    },


    treePathFromId : function(id)
    {
        var sep = this.fileSystem.pathSeparator;
        var dir = id.charAt(id.length-1) == sep;
        if (dir) id = id.substring(0, id.length-1);
        var parts = id.split(sep);
        var folder = "";;
        var treePath = "";
        for (var i=0 ; i<parts.length ; i++)
        {
            folder += parts[i] + ((i<parts.length-1 || dir) ? sep : "");
            treePath += ";" + folder;
        }
        return treePath;
    },

    updateFileDetails : function(record)
    {
        try
        {
            var el = Ext.get('file-details');
            if (!el)
                return;
            var elStyle = el.dom.style;
            elStyle.backgroundColor = "#f0f0f0";
            elStyle.height = "100%";
            elStyle.width = "100%";
            if (!record || !record.data)
            {
                el.update('&nbsp;');
                return;
            }
            var html = [];
            var data = record.data;
            var row = function(label,value)
            {
                html.push("<tr><th style='text-align:right; color:#404040'>");
                html.push(label);
                html.push(":</th><td style='font-size:110%;'>");
                html.push(value);
                html.push("</td></tr>");
            };
            html.push("<p style='font-size:133%; padding:8px;'>");
            html.push(h(data.name));
            html.push("</p>")
            html.push("<table style='padding-left:30px;'>");
            if (data.modified)
                row("Date Modified", _longDateTime(data.modified));
            if (data.file)
                row("Size",data.size);
            if (data.modifiedby)
                row("Modified By", data.modifiedby);
            html.push("</table>");
            el.update(html.join(""));
        }
        catch (x)
        {
            log(x);
        }
    },

    start : function()
    {
        var root = this.tree.getRootNode();
        this.tree.getRootNode().expand();
        this.selectRecord(root.record);
        this.changeDirectory(root.record);
    },

    init : function(config)
    {
        this.fileSystem = config.fileSystem;

        //
        // GRID
        //
        this.store = new Ext.data.Store();
        this.grid = new Ext.grid.GridPanel(
        {
            store: this.store,
            border:false,
            columns: [
                {header: "", width:20, dataIndex: 'iconHref', sortable: false, hiddenn:false, renderer:renderIcon},
                {header: "Name", width: 150, dataIndex: 'name', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode},
//                {header: "Created", width: 150, dataIndex: 'created', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Modified", width: 150, dataIndex: 'modified', sortable: true, hidden:false, renderer:renderDateTime},
                {header: "Size", width: 80, dataIndex: 'size', sortable: true, hidden:false, align:'right', renderer:renderFileSize}
            ]
        });
        this.grid.getSelectionModel().on(ROWSELECTION_MODEL.rowselect, this.Grid_onRowselect, this);
        this.grid.on(GRIDPANEL_EVENTS.celldblclick, this.Grid_onCelldblclick, this);

        //
        // TREE
        //

        var treeloader = new FileSystemTreeLoader({fileSystem: this.fileSystem, displayFiles:false});
        var rootPath = this.fileSystem.rootPath;
        var root = treeloader.createNodeFromRecord(new Ext.data.Record({path:rootPath, file:false, name:rootPath}, rootPath));
        this.tree = new Ext.tree.TreePanel(
        {
            loader:treeloader,
            root:root,
            rootVisible:false,
            title: 'Folders',
            useArrows:true,
            autoScroll:true,
            animate:true,
            enableDD:false,
            containerScroll:true,
            border:false,
            pathSeparator:';'
        });
        this.tree.getSelectionModel().on(TREESELECTION_EVENTS.selectionchange, this.Tree_onSelectionchange, this);

        //
        // LAYOUT
        //
        this.actions =
        {
            downloadAction: this.getDownloadAction(),
            parentFolderAction: this.getParentFolderAction(),
            refreshAction: this.getRefreshAction()
        };
        var tbarConfig =
            [
                {
                    text: 'Action Menu',
                    menu: [this.action]
                },
                this.actions.downloadAction,
                this.actions.parentFolderAction,
                this.actions.refreshAction
            ];
        var layoutItems = [];
        if (this.showAddressBar)
            layoutItems.push(
            {
                region: 'north',
                height: 24,
                margins: '5 5 0 5',
                layout: 'fit',
                border: false,
                items: [{id:'addressBar', html: 'address bar'}]
            });
        if (this.showDetails)
            layoutItems.push(
            {
                region: 'south',
                height: 100,
                minSize: 75,
                maxSize: 250,
                margins: '0 5 5 5',
                layout: 'fit',
                items: [{html:'<div style="background-color:#f0f0f0;height:100%;width:100%">&nbsp</div>', id:'file-details'}]
            });
        if (this.showAddressBar)
            layoutItems.push(
            {
                region:'west',
                id:'west-panel',
                split:true,
                width: 200,
                minSize: 100,
                collapsible: true,
                margins:'5 0 5 5',
                layout:'accordion',
                layoutConfig:{animate:true},
                items: [
                    this.tree,
                    {
                        title:'Settings',
                        html:'<p>Some settings in here.</p>',
                        border:false,
                        iconCls:'settings'
                    }]
            });
        layoutItems.push(
            {
                region:'center',
                margins:'5 0 5 0',
                minSize: 200,
                layout:'fit',
                items: [this.grid]
            });
        if (this.showProperties)
            layoutItems.push(
            {
                title: 'Properties',
                region:'east',
                split:true,
                margins:'5 5 5 0',
                width: 150,
                minSize: 100,
                border: false,
                layout: 'fit',
                items: [{html:'<iframe id=auditFrame height=100% width=100% border=0 style="border:0px;" src="about:blank"></iframe>'}]
            });

        var border = new Ext.Panel(
        {
            id:'borderLayout',
            height:600, width:800,
            layout:'border',
            tbar: tbarConfig,
            items: layoutItems
        });

        border.render(config.renderTo);

        var resizer = new Ext.Resizable('borderLayout', {
            width:800, height:600,
            minWidth:640,
            minHeight:400});
        resizer.on("resize", function(o,width,height){
            border.setWidth(width);
            border.setHeight(height);
        });

        //
        // EVENTS (tie together components)
        //

        this.on(BROWSER_EVENTS.selectionchange, function(record)
        {
            this.updateFileDetails(record);  //undone pass in record
            if (record && record.data & record.data.file && record.data.href)
                this.actions.downloadAction.enable();
            else
                this.actions.downloadAction.disable();
        }, this);

        this.on(BROWSER_EVENTS.directorychange,function(record)
        {
            this.fileSystem.listFiles(record.data.path, (function(filesystem, success, path, records)
            {
                if (success)
                {
                    this.store.removeAll();
                    this.store.add(records);
                }
            }).createDelegate(this));
        }, this);

        this.on(BROWSER_EVENTS.directorychange, function(record)
        {
            this.generateAddressBar(record.data.path);
        }, this);
    }
});


