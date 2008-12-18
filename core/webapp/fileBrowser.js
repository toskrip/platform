document.write(
        "<style>" +
        ".refreshIcon {background-image: url(" + LABKEY.contextPath + "/_images/reload.png)}"+
        "</style>");


/*
	parseUri 1.2.1
	(c) 2007 Steven Levithan <stevenlevithan.com>
	MIT License
*/
function parseUri(str)
{
    var	o   = parseUri.options;
    var m   = o.parser[o.strictMode ? "strict" : "loose"].exec(str);
    var uri = {};
    var i   = 14;

    while (i--)
        uri[o.key[i]] = m[i] || "";

    if (!uri.protocol)
    {
        var l = window.location;
        uri.protocol = uri.protocol || l.protocol;
        uri.port = uri.port || l.port;
        uri.hostname = uri.hostname || l.hostname;
        uri.host = uri.host || l.host;
    }

    uri[o.q.name] = {};
    uri[o.key[12]].replace(o.q.parser, function ($0, $1, $2)
    {
        if ($1) uri[o.q.name][$1] = $2;
    });
    uri.toString = function()
    {
        return this.protocol + "://" + this.host + this.pathname + this.search;
    };
    uri.href = uri.toString();
    return uri;
}
parseUri.options =
{
	strictMode: false,
	key: ["source","protocol","host","userInfo","user","password","hostname","port","relative","pathname","directory","file","search","hash"],
	q:
    {
		name:   "query",
		parser: /(?:^|&)([^&=]*)=?([^&]*)/g
	},
	parser:
    {
		strict: /^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,
		loose:  /^(?:(?![^:@]+:[^:@\/]*@)([^:\/?#.]+):)?(?:\/\/)?((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?)(((\/(?:[^?#](?![^?#\/]*\.[^?#\/.]+(?:[?#]|$)))*\/?)?([^?#\/]*))(?:\?([^#]*))?(?:#(.*))?)/
	}
};
/* end parseUri */

var TREESELECTION_EVENTS =
{
    selectionchange:"selectionchange",
    beforeselect:"beforeselect"
};
var PANEL_EVENTS =
{
	active:'activate',
	add:'add',
	afterlayout:'afterlayout',
	beforeadd:'beforeadd',
	beforeclose:'beforeclose',
	beforecollapse:'beforecollapse',
	beforedestroy:'beforedestroy',
	beforeexpand:'beforeexpand',
	beforehide:'beforehide',
	beforeremove:'beforeremove',
	beforerender:'beforerender',
	beforeshow:'beforeshow',
	beforestaterestore:'beforestaterestore',
	beforestatesave:'beforestatesave',
	bodyresize:'bodyresize',
	close:'close',
	collapse:'collapse',
	deactivate:'deactivate',
	destroy:'destroy',
	disable:'disable',
	enable:'enable',
	expand:'expand',
	hide:'hide',
	move:'move',
	remove:'remove',
	render:'render',
	resize:'resize',
	show:'show',
	staterestore:'staterestore',
	statesave:'statesave',
	titlechange:'titlechange'
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
Ext.apply(GRIDPANEL_EVENTS,PANEL_EVENTS);

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
function startsWith(s, f)
{
    var len = f.length;
    if (s.length < len) return false;
    if (len == 0)
        return true;
    return s.charAt(0) == f.charAt(0) && s.charAt(len-1) == f.charAt(len-1) && s.indexOf(f) == 0;
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
//      uri (string, urlencoded), path (string, not encoded), name (string), file (bool), created (date), modified (date), size (int), iconHref(string)

var FILESYSTEM_EVENTS = {listfiles:"listfiles"};

var FileSystem = function(config)
{
    this.directoryMap = {}; //  map<path,(time,[records])>
    this.addEvents(FILESYSTEM_EVENTS.listfiles);
};

Ext.extend(FileSystem, Ext.util.Observable,
{
    rootPath : "/",
    separator : "/",

    listFiles : function(path, callback)    // callback(filesystem, success, path, records)
    {
        var files = this.directoryFromCache(path);
        if (files)
        {
            if (typeof callback == "function")
                callback.defer(1, null, [this, true, path, files]);
        }
        else
        {
            var ok = this.reloadFiles(path, callback);
            if (!ok && typeof callback == "function")
                callback.defer(1, null, [this, false, path]);
        }
    },


    createDirectory : function(path, callback) // callback(filesystem, success, path)
    {
        callback(this, path, false);
    },


    // called by listFiles(), return false on immediate fail
    reloadFiles : function(path, callback)
    {
        return false;
    },

    getHistory : function(path, callback)
    {
    },

    // protected

    _addFiles : function(path, records)
    {
        this.directoryMap[path] = records;
        this.fireEvent(FILESYSTEM_EVENTS.listfiles, path, records);
    },

    directoryFromCache : function(path)
    {
        var files = this.directoryMap[path];
        if (!files && path && path.length>0 && path.charAt(path.length-1) == this.separator)
            path = path.substring(0,path.length-1);
        files = this.directoryMap[path];
        return files;
    },

    recordFromCache : function(path)
    {
        if (!path || path == this.rootPath)
            return this.rootRecord;
        var parent = this.parentPath(path) || this.rootPath;
        var name = this.fileName(path);
        var files = this.directoryFromCache(parent);
        if (!files)
            return null;
        for (var i=0 ; i<files.length ; i++)
        {
            var r = files[i];
            if (r.data.name == name)
                return r;
        }
        return null;
    },


    // util

    concatPaths : function(a,b)
    {
        var c = 0;
        if (a.length > 0 && a.charAt(a.length-1)==this.separator) c++;
        if (b.length > 0 && b.charAt(0)==this.separator) c++;
        if (c == 0)
            return a + this.separator + b;
        else if (c == 1)
            return a + b;
        else
            return a + b.substring(1);
    },

    parentPath : function(p)
    {
        if (!p)
            p = this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
        return i == -1 ? this.rootPath : p.substring(0,i+1);
    },

    fileName : function(p)
    {
        if (!p || p == this.rootPath)
            return this.rootPath;
        if (p.length > 1 && p.charAt(p.length-1) == this.separator)
            p = p.substring(0,p.length-1);
        var i = p.lastIndexOf(this.separator);
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
// rootName: display name for the root

var WebdavFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {
        baseUrl: LABKEY.contextPath + "/_webdav",
        rootPath: "/",
        rootName : (LABKEY.serverName || "LabKey Server")
    });
    var prefix = this.concatPaths(this.baseUrl, this.rootPath);
    if (prefix.length > 0 && prefix.charAt(prefix.length-1) == this.separator)
        prefix = prefix.substring(0,prefix.length-1);
    this.prefixUrl = prefix;
    WebdavFileSystem.superclass.constructor.call(this);

    var getURI = function(v,rec)
    {
        if (!rec.uriOBJECT)
            rec.uriOBJECT = parseUri(v);
        return rec.uriOBJECT;
    };

    this.FileRecord = Ext.data.Record.create(
        [
            {name: 'uri', mapping: 'href',
                convert : function(v, rec)
                {
                    var uri = getURI(v,rec);
                    return uri ? uri.href : "";
                }
            },
            {name: 'path', mapping: 'href',
                convert : function (v, rec)
                {
                    var uri = getURI(v,rec);
                    var path = uri.pathname;
                    if (prefix)
                        path = path.replace(prefix, "");
                    return decodeURI(path);
                }
            },
            {name: 'name', mapping: 'propstat/prop/displayname'},
            {name: 'file', mapping: 'href', type: 'boolean',
                convert : function (v, rec)
                {
                    // UNDONE: look for <collection>
                    var uri = getURI(v, rec);
                    var path = uri.pathname;
                    return path.length > 0 && path.charAt(path.length-1) != '/';
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

    this.rootRecord = new this.FileRecord({
        id:"/",
        path:"/",
        name: this.rootName,
        file:false,
        iconHref: LABKEY.contextPath + "/_images/labkey.png"
    }, "/");
};

Ext.extend(WebdavFileSystem, FileSystem,
{
    createDirectory : function(path, callback)
    {
        try
        {
            var fileSystem = this;
            var client = new XMLHttpRequest();
            client.onreadystatechange = function()
                {
                    if (client.readyState == 4)
                    {
                        if (200 == client.status || 201 == client.status)   // OK, CREATED
                            callback(fileSystem, true, path);
                        else if (405 == client.status) // METHOD_NOT_ALLOWED
                            callback(fileSystem, false, path);
                        else
                            window.alert(client.statusText);
                    }
                };
            var resourcePath = this.concatPaths(this.prefixUrl, path);
            client.open("POST", resourcePath);
            client.setRequestHeader("method", "MKCOL");
            client.send(null);
        }
        catch (x)
        {
            window.alert(x);
        }
        return true;
    },

    reloadFiles : function(path, callback)
    {
        var url = this.concatPaths(this.prefixUrl, encodeURI(path));
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

// CONFIG
//  required
//      getDropApplet : function()
var AppletFileSystem = function(config)
{
    config = config || {};
    Ext.apply(this, config, {
        path: Ext.isWindows ? "\\" : "/",
        rootName: "My Computer"
    });
    WebdavFileSystem.superclass.constructor.call(this);
    this.FileRecord = Ext.data.Record.create(['uri', 'path', 'name', 'file', 'created', 'modified', 'modifiedby', 'size', 'iconHref']);
    this.connection = new Ext.data.Connection({method: "GET", headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"}});
    this.proxy = new Ext.data.HttpProxy(this.connection);
    this.reader = new Ext.data.XmlReader({record : "response", id : "path"}, this.FileRecord);

    this.rootRecord = new this.FileRecord(
    {
        id:"/",
        path:"/",
        name:"My Computer",
        file:false,
        iconHref: LABKEY.contextPath + "/_images/computer.png"
    }, "/");
};

Ext.extend(AppletFileSystem, FileSystem,
{
    rootPath : "/",
    separator : "/",
    retry : 0,

    createDirectory : function(path, callback)
    {
        // UNDONE:
        alert("NYI");
    },

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
            var uri = applet.local_getUri(i);
            var ts = applet.local_getTimestamp(i);
            var lastModified = ts ? new Date(ts) : null;
            var size = applet.local_getSize(i);
            var data = {id:path, uri:uri, path:path, name:name, file:file, modified:lastModified, size:size, iconHref:file?null:this.FOLDER_ICON, modifiedby:null};
            records.push(new this.FileRecord(data, path));
        }
        this._addFiles(directory, records);
        if (typeof callback == "function")
            callback.defer(1, null, [this, true, directory, records]);
        return true;
    }
});


//
// FileListMenu
//
var FileListMenu = function(fileSystem, path, fn)
{
    FileListMenu.superclass.constructor.call(this, {items:[], cls:'extContainer'});
    this.showFiles = false;
    this.fileSystem = fileSystem;
    this.path = path;
    var records = fileSystem.directoryFromCache(path);
    var populate = function(filesystem, success, path, records)
        {
            for (var i=0 ; i<records.length ; i++)
            {
                var record = records[i];
                var data = record.data;
                if (!this.showFiles && data.file)
                    continue;
                this.addMenuItem(new Ext.menu.Item({text:data.name, icon:data.iconHref, record:record}));
            }
        };
    if (records)
        populate.call(this, null, true, path, records);
    else
        fileSystem.listFiles(path, populate.createDelegate(this));
    if (typeof fn == "function")
    {
        this.on("click", function(menu,item,event)
        {
            var record = item.initialConfig.record;
            fn(record.data.path);
        });
    }
};
Ext.extend(FileListMenu, Ext.menu.Menu);


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
        {
            n.record = record;
            if (record.data.iconHref)
                n.attributes.icon = record.data.iconHref;
        }
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
            if (node.childNodes.length == 1)
            {
                var child = node.firstChild;
                if (!child.isLeaf())
                    child.expand();
            }
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


var BROWSER_EVENTS = {selectionchange:"selectionchange", directorychange:"directorychange"};

// configuration
//  required
//      filesystem
//  optional
//      resizable : true
//      resizable : true,
//      showFolders: true,
//      showAddressBar: true,
//      showDetails: true,
//      showProperties: false,
//
var FileBrowser = function(config)
{
    config = config || {};
    Ext.apply(this.actions, config.actions || {});
    delete config.actions;
    Ext.apply(this, config);
    this.addEvents( [ BROWSER_EVENTS.selectionchange, BROWSER_EVENTS.directorychange ]);
    this.__init__(config);
};
Ext.extend(FileBrowser, Ext.Panel,
{
    FOLDER_ICON: LABKEY.contextPath + "/" + LABKEY.extJsRoot + "/resources/images/default/tree/folder.gif",

    // instance/config variables
    resizable : true,
    showFolders: true,
    showAddressBar: true,
    showDetails: true,
    showProperties: true,

    grid: null,
    store: null,

    currentDirectory: null,
    selectedRecord: null,

    //
    // actions
    //

    actions : {},

    action : new Ext.Action({text: 'Alert', iconCls: 'blist', scope: this, handler: function()
    {
        window.alert('Click','You clicked on "Action 1".');
    }}),

    getDownloadAction : function()
    {
        return new Ext.Action({text: 'Download', scope: this, handler: function()
            {
                if (this.selectedRecord && this.selectedRecord.data.file && this.selectedRecord.data.uri)
                    window.location = this.selectedRecord.data.uri + "?contentDisposition=attachment";
            }});
    },

    getCreateDirectoryAction : function()
    {
        return new Ext.Action({text: 'Create Folder', scope: this, handler: function()
        {
            var p = this.currentDirectory.data.path;
            var folder = prompt( "Folder Name", "New Folder");
            if (!folder)
                return;
            var path = this.fileSystem.concatPaths(p, folder);
            this.fileSystem.createDirectory(path, function(fs, success, path)
            {
                if (success)
                {
                    if (this.actions.refresh)
                        this.actions.refresh.execute();
                    this.selectFile(path);
                }
            }.createDelegate(this));
        }.createDelegate(this)});
    },

    getParentFolderAction : function()
    {
        return new Ext.Action({text: 'Up', scope: this, handler: function()
        {
            // CONSIDER: PROPFIND to ensure this link is still good?
            var p = this.currentDirectory.data.path;
            var dir = this.fileSystem.parentPath(p) || this.fileSystem.rootPath;
            this.changeDirectory(dir);
        }});
    },

    getRefreshAction : function()
    {
        return new Ext.Action({text: 'Refresh', scope:this, iconCls:'refreshIcon', handler: function()
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

    helpEl : null,
    helpWindow : null,

    getHelpAction : function()
    {
        if (!this.helpEl)
            return null;
        return new Ext.Action({text: 'Help', scope:this, handler: function()
        {
            if (!this.helpWindow)
            {
                var w = new Ext.Window({contentEl:this.helpEl});
                w.closeAction = 'hide';
                this.helpWindow = w;
            }
            this.helpWindow.show();
        }});
    },

    changeDirectory : function(record)
    {
        if (typeof record == "string")
        {
            var path = record;
            record = this.fileSystem.recordFromCache(path);
            if (!record)
            {
                var parent = this.fileSystem.parentPath(path);
                this.fileSystem.listFiles(parent, function(filesystem, success, parentPath, records)
                {
                    record = this.fileSystem.recordFromCache(path);
                    if (record)
                        this.changeDirectory(record);
                }.createDelegate(this));
            }
        }

        if (record && !record.data.file && this.currentDirectory != record)
        {
            this.currentDirectory = record;
            this.fireEvent(BROWSER_EVENTS.directorychange, record);
        }
    },

    selectFile : function(record)
    {
        if (typeof record == "string")
        {
            var path = record;
            record = this.fileSystem.recordFromCache(path);
            if (!record)
            {
                var parent = this.fileSystem.parentPath(path);
                this.fileSystem.listFiles(parent, function(filesystem, success, parentPath, records)
                {
                    record = this.fileSystem.recordFromCache(path);
                    if (record)
                        this.selectFile(record);
                }.createDelegate(this));
            }
        }
        if (!this.selectedRecord || this.selectedRecord.data.path != record.data.path)
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
            this.selectFile(record);
    },

    Grid_onKeypress : function(e)
    {
        switch (e.keyCode)
        {
            case e.ENTER:
                    var record = this.selectedRecord;
                    if (record && !record.data.file)
                        this.changeDirectory(record);
                    this.grid.focus();
                break;
            default:
                break;
        }
    },

    Grid_onCelldblclick : function(grid, rowIndex, columnIndex, e)
    {
        var record = grid.getStore().getAt(rowIndex);
        this.selectFile(record);
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
        this.grid.focus();
    },

    Tree_onSelectionchange : function(sm, node)
    {
        if (this.grid)
            this.grid.getSelectionModel().clearSelections();
        if (node)
        {
            this.selectFile(node.record);
            this.changeDirectory(node.record);
        }
    },


    addressBarHandler : null,

    updateAddressBar : function(path)
    {
        var el = Ext.get('addressBar');
        if (!el)
            return;
        if (this.addressBarHandler)
        {
            el.un("click", this.addressBarHandler);
            this.addressBarHandler = null;
        }
        var text;
        if (path == this.fileSystem.rootPath)
            text = h(this.fileSystem.rootRecord.data.name);
        else
            text = h(path);
        el.update(text);
        this.addressBarHandler = this.showFileListMenu.createDelegate(this, [path]);
        el.on("click", this.addressBarHandler);
    },

    showFileListMenu : function(path)
    {
        var el = Ext.get('addressBar');
        var menu = new FileListMenu(this.fileSystem, path, this.changeDirectory.createDelegate(this));
        menu.show(el);
    },

    ancestry : function(id)
    {
        var path = id;
        var a = [path];
        while (true)
        {
            var parent = this.fileSystem.parentPath(path);
            if (!parent || parent == path)
                break;
            a.push(parent);
            path = parent;
        }
        a.reverse();
        return a;
    },

    treePathFromId : function(id)
    {
        var a = this.ancestry(id);
        return a.join(";");
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
            html.push("</p>");
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

    start : function(wd)
    {
        var root = this.tree.getRootNode();
        root.expand();
        if (typeof wd == "string")
        {
            this.changeDirectory(wd);
            this.selectFile(wd);
        }
        else
        {
            this.selectFile(root.record);
            this.changeDirectory(root.record);
        }
    },


    __init__ : function(config)
    {
        //
        // GRID
        //
        this.store = new Ext.data.Store({recordType:this.fileSystem.FileRecord});
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
        this.grid.on(GRIDPANEL_EVENTS.keypress, this.Grid_onKeypress, this);
        this.grid.on(PANEL_EVENTS.render, function()
        {
            this.getView().hmenu.getEl().addClass("extContainer");
            this.getView().colMenu.getEl().addClass("extContainer");
        }, this.grid);

        //
        // TREE
        //

        var treeloader = new FileSystemTreeLoader({fileSystem: this.fileSystem, displayFiles:false});
        var root = treeloader.createNodeFromRecord(this.fileSystem.rootRecord, this.fileSystem.rootPath);
        this.tree = new Ext.tree.TreePanel(
        {
            loader:treeloader,
            root:root,
            rootVisible:true,
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
            download: this.getDownloadAction(),
            parentFolder: this.getParentFolderAction(),
            refresh: this.getRefreshAction(),
            help: this.getHelpAction(),
            createDirectory: this.getCreateDirectoryAction()
        };
        var tbarConfig =
            [
                {
                    text: 'Action Menu',
                    menu: { cls: 'extContainer', items: [this.action] }
                },
                this.actions.createDirectory,
                this.actions.download,
                this.actions.parentFolder,
                this.actions.refresh
            ];
        if (this.actions.help)
            tbarConfig.push(this.actions.help);
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

        var renderTo = config.renderTo || null;
        Ext.apply(config, {layout:'border', tbar:tbarConfig, items: layoutItems, renderTo:null}, {id:'fileBrowser', height:600, width:800});
        FileBrowser.superclass.constructor.call(this, config);
//        var border = new Ext.Panel(config);

//        var resizer = new Ext.Resizable(config.id, {
//            width:800, height:600,
//            minWidth:640,
//            minHeight:400});
//        resizer.on("resize", function(o,width,height){
//            this.setWidth(width);
//            this.setHeight(height);
//        }.createDelegate(this));

        //
        // EVENTS (tie together components)
        //

        this.on(BROWSER_EVENTS.selectionchange, function(record)
        {
            this.updateFileDetails(record);
            if (record && record.data && record.data.file && record.data.uri)
                this.actions.download.enable();
            else
                this.actions.download.disable();
        }, this);

        this.on(BROWSER_EVENTS.directorychange, function(record)
        {
            if (record && record.data && record.data.path != this.fileSystem.rootPath)
                this.actions.parentFolder.enable();
            else
                this.actions.parentFolder.disable();
        }, this);

        this.on(BROWSER_EVENTS.directorychange,function(record)
        {
            this.store.removeAll();
            this.fileSystem.listFiles(record.data.path, (function(filesystem, success, path, records)
            {
                if (success && this.currentDirectory.data.path == path)
                {
                    this.store.removeAll();
                    this.store.add(records);
                }
            }).createDelegate(this));
        }, this);

        this.on(BROWSER_EVENTS.directorychange, function(record)
        {
            this.updateAddressBar(record.data.path);
        }, this);

        if (renderTo)
            this.render(renderTo);
    }
});


