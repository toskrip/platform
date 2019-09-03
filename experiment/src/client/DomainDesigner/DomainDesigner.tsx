/*
 * Copyright (c) 2019 LabKey Corporation
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

import {List} from "immutable";
import * as React from 'react'
import {Button} from "react-bootstrap";
import {ActionURL} from "@labkey/api";
import {LoadingSpinner, Alert, ConfirmModal, WizardNavButtons} from "@glass/base";
import {DomainForm, DomainDesign, clearFieldDetails, fetchDomain, saveDomain, SEVERITY_LEVEL_ERROR, SEVERITY_LEVEL_WARN, IBannerMessage, getBannerMessages} from "@glass/domainproperties"

interface IAppState {
    dirty: boolean
    domain: DomainDesign
    domainId: number
    messages?: List<IBannerMessage>,
    queryName: string
    returnUrl: string
    schemaName: string
    showConfirm: boolean
    submitting: boolean
}

export class App extends React.PureComponent<any, Partial<IAppState>> {

    constructor(props) {
        super(props);

        const { domainId, schemaName, queryName, returnUrl } = ActionURL.getParameters();

        let messages = List<IBannerMessage>().asMutable();
        if ((!schemaName || !queryName) && !domainId) {
            let msg =  'Missing required parameter: domainId or schemaName and queryName.';
            let msgType = 'danger';
            let bannerMsg ={message : msg, messageType : msgType};
            messages.push(bannerMsg);
        }

        this.state = {
            schemaName,
            queryName,
            domainId,
            returnUrl,
            submitting: false,
            messages: messages.asImmutable(),
            showConfirm: false,
            dirty: false
        };
    }

    componentDidMount() {
        const { schemaName, queryName, domainId, messages } = this.state;

        if ((schemaName && queryName) || domainId) {
            fetchDomain(domainId, schemaName, queryName)
                .then(domain => {
                    this.setState(() => ({domain}));
                })
                .catch(error => {
                    this.setState(() => ({
                        messages : messages.set(0, {message: error.exception, messageType: 'danger'})
                    }));
                });
        }

        window.addEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    handleWindowBeforeUnload = (event) => {

        if (this.state.dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    componentWillUnmount() {
        window.removeEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    submitHandler = (navigate : boolean) => {
        const { domain, submitting } = this.state;

        if (submitting) {
            return;
        }

        this.setState({
            submitting: true
        });

        saveDomain(domain)
            .then((savedDomain) => {

                this.setState(() => ({
                    domain: savedDomain,
                    submitting: false,
                    dirty: false
                }));

                this.showMessage("Save Successful", 'info', 0);
                window.scrollTo(0, 0);

                if (navigate) {
                    this.navigate();
                }
            })
            .catch((badDomain) => {

                let bannerMsgs = getBannerMessages(badDomain);

                window.scrollTo(0, 0);

                this.setState(() => ({
                    domain: badDomain,
                    submitting: false,
                    messages: bannerMsgs
                }));
            })
    };

    onChangeHandler = (newDomain, dirty) => {

        let bannerMsgs = getBannerMessages(newDomain);

        this.setState((state) => ({
            domain: newDomain,
            dirty: state.dirty || dirty, // if the state is already dirty, leave it as such
            messages: bannerMsgs
        }));
    };

    dismissAlert = (index: any) => {
        this.setState(() => ({
            messages: this.state.messages.setIn([index], [{message: undefined, messageType: undefined}])
        }));
    };

    showMessage = (message: string, messageType: string, index: number, additionalState?: Partial<IAppState>) => {

        const { messages } = this.state;

        this.setState(Object.assign({}, additionalState, {
            messages : messages.set(index, {message: message, messageType: messageType})
        }));
    };

    onCancelBtnHandler = () => {
        if (this.state.dirty) {
            this.setState(() => ({showConfirm: true}));
        }
        else {
            this.navigate();
        }
    };

    navigate = () => {
        const { returnUrl } = this.state;
        this.setState(() => ({dirty: false}), () => {
            // TODO if we don't have a returnUrl, should we just do a goBack()?
            window.location.href = returnUrl || ActionURL.buildURL('project', 'begin');
        });
    };

    hideConfirm = () => {
        this.setState(() => ({showConfirm: false}));
    };

    renderNavigateConfirm() {
        return (
            <ConfirmModal
                title='Confirm Leaving Page'
                msg='You have unsaved changes. Are you sure you would like to leave this page before saving your changes?'
                confirmVariant='success'
                onConfirm={this.navigate}
                onCancel={this.hideConfirm}
            />
        )
    }

    renderButtons() {
        const { submitting, dirty } = this.state;

        return (
            <WizardNavButtons
                cancel={this.onCancelBtnHandler}
                containerClassName=""
                includeNext={false}>
                <Button
                    type='submit'
                    bsClass='btn'
                    onClick={() => this.submitHandler(false)}
                    disabled={submitting || !dirty}>
                    Save
                </Button>
                <Button
                    type='submit'
                    bsClass='btn btn-success'
                    onClick={() => this.submitHandler(true)}
                    disabled={submitting || !dirty}>
                    Save And Finish
                </Button>
            </WizardNavButtons>
        )
    }

    render() {
        const { domain, messages, showConfirm } = this.state;
        const isLoading = domain === undefined && messages === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                { showConfirm && this.renderNavigateConfirm() }
                { messages && messages.size > 0 && messages.map((bannerMessage, idx) => {
                    return (<Alert key={idx} bsStyle={bannerMessage.messageType} onDismiss={() => this.dismissAlert(idx)}>{bannerMessage.message}</Alert>) })
                }
                { domain &&
                    <DomainForm
                        domain={domain}
                        onChange={this.onChangeHandler}
                    />
                }
                { domain && this.renderButtons() }
            </>
        )
    }
}