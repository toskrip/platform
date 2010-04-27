/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Class to display the custom file properties dialog. The will handle either case of single or multiple
 * file uploads and will collect custom properties from the user.
 *
 *
 * Will fire the following events:
 *
 * success : if the update of the actions succeeded.
 * failure : if the action update failed.
 * runSelected : run the selected action, the data record is passed as a parameter
 *
 */
LABKEY.FilePropertiesPanel = Ext.extend(Ext.util.Observable, {

    fileFields : [],            // array of extra field information to collect/display for each file uploaded
    files : [],                 // array of file information for each file being transferred
    fileIndex : 0,
    containerPath : undefined,  // the effective container custom file properties originate from (may be a parent if inherited)

    constructor : function(config)
    {
        LABKEY.FilePropertiesPanel.superclass.constructor.call(this, config);

        Ext.apply(this, config);

        // initialize templates
        this.fileTitle = new Ext.XTemplate('<span class="labkey-mv">File ({count} of {total}) : {name}</span>').compile();

        this.addEvents(
            /**
             * @event filePropConfigChanged
             * Fires after this object is updated with a new set of file properties.
             * @param {LABKEY.FileContentConfig} this
             */
            'filePropConfigChanged',
            /**
             * @event actionConfigChanged
             * Fires after this object is updated with a new set of action configurations.
             */
            'actionConfigChanged'
        );
    },

    show : function(btn)
    {
        // we only want the list of extra columns in the form
        var columns = [];
        for (var i=0; i < this.fileFields.length; i++)
            columns.push(this.fileFields[i].name);

        LABKEY.Query.selectRows({
            schemaName:'exp',
            queryName:'Datas',
            maxRows: 0,
            scope: this,
            containerPath: this.containerPath,
            requiredVersion: '9.1',
            columns: columns.join(','),
            successCallback: this.createFormPanel
        });
    },

    /**
     * Create a form panel from the returned select rows metadata
     * @param data
     * @param resp
     */
    createFormPanel : function(data, resp)
    {
        var fields = [];
        for (var i=0; i < data.metaData.fields.length; i++)
        {
            var field = data.metaData.fields[i];
            if (field.name != 'RowId')
            {
                if (field.lookup)
                    field.lookup.container = this.containerPath; 
                fields.push(field);
            }
        }

        var cm = [];
        for (var i=0; i < data.columnModel.length; i++)
        {
            var col = data.columnModel[i];
            col.editable = true;

            cm.push(col);
        }
        var buttons;
        this.doneButton = new Ext.Button({text:"Done", scope: this, handler:this.onDone});

        if (this.files.length > 1)
        {
            this.applyCheckbox = new Ext.form.Checkbox({boxLabel:"Apply to all remaining files", scope:this, handler:this.onApplyAll });
            this.doneButton.setDisabled(true);

            this.prevButton = new Ext.Button({text:'< Prev', enabled:false, scope:this, handler:this.onPrev});
            this.nextButton = new Ext.Button({text:'Next >', scope:this, handler:this.onNext});
            buttons = [this.prevButton, this.nextButton, this.doneButton];
        }
        else
            buttons = [this.doneButton];
        buttons.push({text:'Cancel', scope:this, handler:function(){this.win.close();}});

        this.formPanel = new LABKEY.ext.FormPanel({
            addAllFields: true,
            border: false,
            autoScroll: true,
            flex: 1,
            columnModel: cm,
            metaData: {fields: fields}
        });
        this.formPanel.add({name: 'uri', xtype: 'hidden', value: 0});

        var titlePanel = new Ext.Panel({
            id: 'file-props-title',
            border: false,
            html: this.fileTitle.apply({count:1, total:this.files.length, name:this.files[0].name}),
            height: 25
        });
        var statusPanel = new Ext.Panel({
            id: 'file-props-status',
            border: false,
            height: 40
        })

        var panelItems = [titlePanel, this.formPanel];
        if (this.applyCheckbox)
            panelItems.push(this.applyCheckbox);
        panelItems.push(statusPanel);

        var panel = new Ext.Panel({
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            bodyStyle : 'padding:10px;',
            items: panelItems
        });

        this.win = new Ext.Window({
            title: 'Extended File Properties',
            width: 500,
            height: 400,
            cls: 'extContainer',
            autoScroll: true,
            closeAction:'close',
            modal: true,
            layout: 'fit',
            items: [panel],
            buttons: buttons
/*
            buttons: [{
                text: 'Submit',
                id: 'btn_submit',
                listeners: {click:function(button, event) {
                    var form = formPanel.getForm();

                    if (form && !form.isValid())
                    {
                        Ext.Msg.alert('Extended File Properties', 'Not all fields have been properly completed');
                        return false;
                    }

                    form.doAction('submit', {
                        url: LABKEY.ActionURL.buildURL("filecontent", "saveCustomFileProps"),
                        waitMsg:'Submiting Form...',
                        method: 'POST',
                        success: function(){
                            this.refreshDirectory();
                            win.close();
                        },
                        failure: function(form, action){
                            var errorTxt = 'An error occurred submitting the .';
                            var jsonResponse = Ext.util.JSON.decode(action.response.responseText);
                            if (jsonResponse && jsonResponse.errors)
                            {
                                errorTxt = '<span class="labkey-error">' + jsonResponse.errors._form + '</span>'
                            }
                            var el = Ext.get('file-props-status');
                            if (el)
                                el.update(errorTxt);
                        },
                        scope: this,
                        clientValidation: false
                    });},
                    scope:this}
            },{
                text: 'Cancel',
                id: 'btn_cancel',
                handler: function(){win.close();}
            }]
*/
        });
        this.win.show();
    },

    onDone : function()
    {
        this.saveFormValues();
        var data = {files: this.files};

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("filecontent", "updateFileProps"),
            method : 'POST',
            scope: this,
            success: function(){
                this.fireEvent('success');
                this.win.close();
            },
            failure: function(response, opt){
                var errorTxt = 'An error occurred submitting the .';
                var jsonResponse = Ext.util.JSON.decode(response.responseText);
                if (jsonResponse && jsonResponse.errors)
                {
                    for (var i=0; i < jsonResponse.errors.length; i++)
                    {
                        var error = jsonResponse.errors[i];
                        errorTxt = '<span class="labkey-error">' + error.message + '</span>'
                    }
                }
                var el = Ext.get('file-props-status');
                if (el)
                    el.update(errorTxt);
            },
            jsonData : data,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    },

    onApplyAll : function(checkbox, state)
    {
        if (state)
        {
            for (var i = this.fileIndex + 1; i < this.files.length; i++)
                Ext.apply(this.files[i], this.formPanel.getForm().getValues());

            this.doneButton.enable();
        }
        //this.updateFormState();
    },

    onPrev : function()
    {
        this.saveFormValues();
        this.applyCheckbox.setValue(false);
        if (this.fileIndex > 0)
            this.fileIndex--;
        this.updateFormState();
    },

    onNext : function()
    {
        this.saveFormValues();
        this.applyCheckbox.setValue(false);
        if (this.fileIndex < this.files.length)
           this.fileIndex++;
        this.updateFormState();
    },

    saveFormValues : function()
    {
        var data = this.files[this.fileIndex];
        Ext.apply(data, this.formPanel.getForm().getValues());
    },

    updateFormState : function()
    {
        var file = this.files[this.fileIndex];
//        Ext.getDom(imgId).src = iconSrc(file.name);

        Ext.getDom('file-props-title').innerHTML = this.fileTitle.apply({count:this.fileIndex+1, total:this.files.length, name:file.name});
        if (this.files.length > 1)
        {
            this.doneButton.setDisabled(this.fileIndex < this.files.length - 1 && !this.applyCheckbox.getValue());
            this.prevButton.setDisabled(this.fileIndex == 0);
            this.nextButton.setDisabled(this.fileIndex == this.files.length -1);
        }
        this.formPanel.getForm().reset();
        this.formPanel.getForm().setValues(file);
    }
});
