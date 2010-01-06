<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.HttpView" %><%
    NavTree tree = (NavTree)HttpView.currentModel();
%>
<script type="text/javascript">
Ext.ux.IFrameComponent = Ext.extend(Ext.BoxComponent,
{
   onRender : function(ct, position)
   {
        this.el = ct.createChild({tag: 'iframe', id: 'iframe-'+ this.id, frameBorder: 0, src: this.url, style:"width:100%;height:100%"});
   },
   onResize : function(adjWidth, adjHeight, rawWidth, rawHeight)
   {
       Ext.get('iframe-' + this.id).setSize(adjWidth, adjHeight);
   }
});

Ext.ux.HtmlComponent = Ext.extend(Ext.Component,
{
    initComponent : function()
    {
        Ext.ux.HtmlComponent.superclass.initComponent.call(this);
        this.addEvents("afterload");
    },
    onRender : function(ct, position)
    {
        this.el = Ext.get(ct);
    },
    onResize : function(adjWidth, adjHeight, rawWidth, rawHeight)
    {
       Ext.get('div-' + this.id).setSize(adjWidth, adjHeight);
    },
    load : function(url)
    {
        this.el.dom.innerHTML = "<b>loading</b><hr>" + url;
        //var conn = Ext.data.Connection();
        //conn.on("requestcomplete",this.onRequestComplete);
        //conn.on("requestexception",function(){window.alert(arguments[0]);});
        var me = this;
        Ext.Ajax.request({
            url:url,
            success:function(response,options){me.success(response,options);},
            failure:function(response,options){this.el.dom.innerHTML = response.statusText;}});
    },
    success:function(response,options)
    {
        this.el.update(response.responseText, true);
        this.fireEvent("afterload", this.el);
    }    
});

Ext.ux.CustomHtmlComponent = Ext.extend(Ext.ux.HtmlComponent,
{
    success:function(response,options)
    {
        //return Ext.ux.CustomHtmlComponent.superclass.success.call(this,response,options);
        // UNDONE: handle logon redirect
        var t = response.responseText.trim();
        if (!startsWith(t.substring(0,5).toLowerCase(),"<html"))
        {
            Ext.ux.CustomHtmlComponent.superclass.success.call(this,response,options);
        }
        else
        {
            //Ext.getDoc().update(response.responseText,true);
            window.location.href = options.url;
        }
    }
})
</script>

<div class="extContainer">
<div id="moduleNav" width="200px" height="100%" style="overflow:auto;border:1px solid #c3daf9;"></div>
</div>

<script type="text/javascript">

var navTreeComponent;
var studyViewComponent;

Ext.Ajax.defaultHeaders = {'template':'custom'};

Ext.onReady(function()
{
    navTreeComponent = new Ext.tree.TreePanel(
    {
        el:'moduleNav',
        width:200,
        autoHeight:true,
        rootVisible:false,
        //title: 'Study Navigator',
        useArrows:true,
        //autoScroll:true,
        animate:true,
        enableDD:false,
        containerScroll:true,
        loader : new Ext.tree.TreeLoader({preloadChildren:true}),
        root: new Ext.tree.TreeNode({id: '/', text:'/', children:moduleNav})
    });

    //navTreeComponent.on("beforeclick", Tree_beforeClick);
    navTreeComponent.getLoader().load(navTreeComponent.root);
    navTreeComponent.render();
    navTreeComponent.root.expand();

    //studyViewComponent = new Ext.ux.HtmlComponent(
    studyViewComponent = new Ext.ux.CustomHtmlComponent(
    {
        el: 'studyDiv'
    });
    studyViewComponent.on("afterload", fixupLinks);
    studyViewComponent.render();

    fixupLinks(Ext.get("studyDiv"));  
});

function fixupLinks(el)
{
    if (!el) return;
    var as = el.dom.getElementsByTagName("a");
    Ext.each(as, fixupLink)
}

function fixupLink(a)
{
    if (!a.href || a.onclick)
        return;
    var href = a.href;
    if (startsWith(href,'javascript:'))
        return;
    a.href = "javascript:onNavigate('" + a.href + "');"
}

function startsWith(s,pre)
{
    return s.indexOf(pre)==0;
}

var baseURL = window.location.href;
i = baseURL.indexOf("/study");
baseURL = baseURL.substring(0,i+"/study".length);

function navigateInPlace(url)
{
    if (studyViewComponent && studyViewComponent.rendered)
        studyViewComponent.load(url);
}

function onNavigate(url)
{
    // need better filter than this, verify container
    if (!startsWith(url,baseURL) ||  -1 == url.indexOf(".view"))
    {
        window.location.href = url;
        return;
    }
    navigateInPlace(url);
}

var nodeDefaults = {};

function translateNode(n)
{
    if ("href" in n)
    {
        n.href = "javascript:navigateInPlace('" + n.href +"')";
    }
    else
    {
        n.href="javascript:return false;";
    }
    Ext.applyIf(n, nodeDefaults);
}

function translateNavTree(n)
{
    if ("children" in n)
        for (var i=0 ; i<n.children.length ; i++)
            translateNavTree(n.children[i]);
    translateNode(n);
}


function updatePageProperties(config)
{
    if (config.permalink && Ext.get("permalink"))
    {
        Ext.get("permalink").dom.href = config.permalink;
    }
    if (config.navtrail)
    {
        var list = [];
        for (var i=0 ; i<config.navtrail.length-1;i++)
            list.push(config.navtrail[i]);
        var title = config.navtrail[i].title;
        if (config.title)
            title = config.title;
        LABKEY.NavTrail.setTrail(title, list);
    }
}

var navTreeRoot = <%=tree.toJS()%>;
translateNavTree(navTreeRoot)
var moduleNav = navTreeRoot.children;


</script>


























