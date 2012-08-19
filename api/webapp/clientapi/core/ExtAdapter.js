LABKEY.initExtAdapter = function(LABKEY, Ext){
    Ext.ns('LABKEY.ExtAdapter');
    //console.log('using Ext version: ' + Ext.version);

    Ext.apply(LABKEY.ExtAdapter, {
        Ajax: Ext.Ajax,

        apply: Ext.apply,

        applyIf: Ext.applyIf,

        decode: Ext.JSON ? Ext.JSON.decode : Ext.util.JSON.decode,

        domAppend: Ext.DomHelper.append,

        each: Ext.each,

        encode: Ext.JSON ? Ext.JSON.encode : Ext.util.JSON.encode,

        EventManager: Ext.EventManager,

        //note: this is still present in Ext4, but is deprecated
        extend: Ext.extend,

        Format: Ext.util.Format,

        get: Ext.get,

        htmlEncode: Ext.util.Format.htmlEncode,

        isArray: Ext.isArray,

        isDefined: Ext.isDefined,

        isFunction: Ext.isFunction,

        isGecko: Ext.isGecko,

        isIE: Ext.isIE,

        isObject: Ext.isObject,

        isPrimitive: Ext.isPrimitive,

        isString: Ext.isString,

        Msg: Ext.Msg,

        namespace: Ext.ns,

        ns: Ext.ns,

        onReady: Ext.onReady,

        query: Ext.query
    });
}

LABKEY.inferHighestExtVersion = function(){
    if (window.Ext4)
        return Ext4;
    else if (window.Ext)
        return Ext;
}

LABKEY.initExtAdapter(LABKEY, LABKEY.inferHighestExtVersion());
