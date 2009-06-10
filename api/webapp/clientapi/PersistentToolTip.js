/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * @private
 * @namespace LabKey ToolTip Class.
 * Unlike Ext.ToolTip, this ToolTip class won't be dismissed when
 * then mouse is hovering over the ToolTip's popup element.
 */
LABKEY.ext.PersistentToolTip = Ext.extend(Ext.ToolTip,{
  initComponent: function() {
    Ext.apply(this,{
      showDelay: 500,
      hideDelay: 1500,
      dismissDelay: 0,
      autoHide: false
      //floating:{shadow:true,shim:true,useDisplay:true,constrain:true}
    });
    LABKEY.ext.PersistentToolTip.superclass.initComponent.call(this);
  },

  afterRender: function() {
    LABKEY.ext.PersistentToolTip.superclass.afterRender.call(this);
    this.el.on('mouseout', this.onTargetOut, this);
    this.el.on('mouseover', this.onElOver, this);
  },

  checkWithin: function(e) {
    if (this.el && e.within(this.el.dom, true)) {
      return true;
    }
    if (this.disabled || e.within(this.target.dom, true)){
      return true;
    }
    return false;
  },

  onElOver: function(e) {
    if (this.checkWithin(e)) {
      this.clearTimer('hide');
    }
  },

  onTargetOver : function(e){
    if (this.disabled || e.within(this.target.dom, true)){
        return;
    }
    this.clearTimer('hide');
    this.targetXY = e.getXY();
    this.delayShow(e);
  },

  delayShow : function(e){
    this.showTimer = this.doShow.defer(this.showDelay, this, [e]);
  },

  doShow: function(e) {
    var xy = e.getXY();
    var within = this.target.getRegion().contains({left: xy[0], right: xy[0], top: xy[1], bottom: xy[1]});
    if (within) {
      this.show();
    }
  },

  onTargetOut: function(e){
    if (this.checkWithin(e)) {
      this.clearTimer('hide');
    } else if (this.hideTimer) {
      this.hide();
    } else {
      this.delayHide();
    }
  }
});
Ext.reg('persistenttip', LABKEY.ext.PersistentToolTip);

/**
 * @private
 * Adds a callout icon after the 'config.target' element.
 * If configued to autoLoad and a 'config.tpl' template is set,
 * the template will be used to render the contents of the tooltip.
 *
 * @example
 &lt;div id='my-div'>hello&lt;/div>
 &lt;script type="text/javascript>
 var queryUrl = LABKEY.ActionURL.buildURL("query", "getQuery");
 var tip = new LABKEY.ext.CalloutTip({
     target: "my-div",
     autoLoad: {
       url: queryUrl,
       params: {
         schemaName: "flow",
         "query.queryName": "Runs",
         "query.FCSFileCount~neq": 0,
         "query.columns": encodeURI("Name,Flag/Comment,FCSFileCount"),
         apiVersion: 9.1
       }
     },
     tpl: new Ext.XTemplate(
       '&lt;table boder=0>',
       '&lt;tpl for="rows">',
       '  &lt;tr>',
       '    &lt;!-- use a subtemplate on the "Name" field to get url and value properties ... -->',
       '    &lt;tpl for="Name">',
       '      &lt;td>&lt;a href="{url}">{value}&lt;/a>',
       '    </tpl>',
       '    &lt;!-- ... or use a template expression -->',
       '    &lt;td align="right">({[values.FCSFileCount.value]} files)',
       '  &lt;/tr>',
       '&lt;/tpl>',
       '&lt;/table>')
 });
 * &lt;/div>
 */
LABKEY.ext.CalloutTip = Ext.extend(LABKEY.ext.PersistentToolTip, {
    targetAutoEl :
            '<span class="labkey-callout-tip">' +
            '<img src="/labkey/_.gif">' +
            '</span>',

    overTargetCls : 'labkey-callout-tip-over',

    initComponent: function()
    {
      Ext.apply(this, {
          mouseOffset: [2, 8],
          autoWidth: true
      });
      LABKEY.ext.CalloutTip.superclass.initComponent.call(this);
    },

    initTarget : function ()
    {
        if (this.target)
        {
            var origTarget = Ext.get(this.target);
            this.target = Ext.DomHelper.append(origTarget, this.targetAutoEl, true);
        }
        LABKEY.ext.CalloutTip.superclass.initTarget.call(this);
    },

    onTargetOver : function (e)
    {
        if (!this.disabled && this.overTargetCls)
        {
            this.target.addClass(this.overTargetCls);
        }
        LABKEY.ext.CalloutTip.superclass.onTargetOver.call(this, e);
    },

    onTargetOut : function (e)
    {
        if (!this.disabled && this.overTargetCls)
        {
            this.target.removeClass(this.overTargetCls);
        }
        LABKEY.ext.CalloutTip.superclass.onTargetOut.call(this, e);
    },

    doAutoLoad : function ()
    {
        if (this.initialConfig.tpl || this.initialConfig.renderer)
        {
            var tpl = this.initialConfig.tpl;
            var renderer = this.initialConfig.renderer;
            var self = this;
            var updaterRenderer = {
                render: function (el, response, updateManager, callback)
                {
                    var json = null;
                    if (response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                            && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
                        json = Ext.util.JSON.decode(response.responseText);

                    // XXX: error handling

                    if (json)
                    {
                        if (tpl)
                            tpl.overwrite(el, json);
                        else if (renderer)
                            renderer(el, json);
                        self.syncSize();

                        LABKEY.Utils.ensureBoxVisible(self);
                    }
                }
            };
            this.body.getUpdater().setRenderer(updaterRenderer);
        }
        LABKEY.ext.CalloutTip.superclass.doAutoLoad.call(this);
    }
});


