/*
  Copyright 2012 -2014 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.pac4j.saml.sso.impl;

import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPPostDecoder;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.saml.context.SAML2MessageContext;
import org.pac4j.saml.crypto.CredentialProvider;
import org.pac4j.saml.exceptions.SAMLException;
import org.pac4j.saml.sso.SAML2MessageReceiver;
import org.pac4j.saml.sso.SAML2ResponseValidator;
import org.pac4j.saml.util.Configuration;

/**
 * @author Misagh Moayyed
 */
public class SAML2WebSSOMessageReceiver implements SAML2MessageReceiver {

    private static final String SAML2_WEBSSO_PROFILE_URI = "urn:oasis:names:tc:SAML:2.0:profiles:SSO:browser";


    private final SAML2ResponseValidator validator;

    private final CredentialProvider credentialProvider;

    public SAML2WebSSOMessageReceiver(final SAML2ResponseValidator validator,
                                      final CredentialProvider credentialProvider) {
        this.validator = validator;
        this.credentialProvider = credentialProvider;
    }

    @Override
    public Credentials receiveMessage(final SAML2MessageContext context) {
        final SAMLPeerEntityContext peerContext = context.getSAMLPeerEntityContext();

        peerContext.setRole(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
        context.getSAMLSelfProtocolContext().setProtocol(SAMLConstants.SAML20P_NS);
        final HTTPPostDecoder decoder = new HTTPPostDecoder();
        try {

            decoder.setHttpServletRequest(context.getProfileRequestContextInboundMessageTransportRequest().getRequest());
            decoder.setParserPool(Configuration.getParserPool());
            decoder.initialize();
            decoder.decode();

        } catch (final Exception e) {
            throw new SAMLException("Error decoding saml message", e);
        }

        final SAML2MessageContext decodedCtx = new SAML2MessageContext(decoder.getMessageContext());
        decodedCtx.setMessage(decoder.getMessageContext().getMessage());
        decodedCtx.setSAMLMessageStorage(context.getSAMLMessageStorage());

        final SAMLBindingContext bindingContext = decodedCtx.getParent()
                .getSubcontext(SAMLBindingContext.class);

        decodedCtx.getSAMLBindingContext().setBindingDescriptor(bindingContext.getBindingDescriptor());
        decodedCtx.getSAMLBindingContext().setBindingUri(bindingContext.getBindingUri());
        decodedCtx.getSAMLBindingContext().setHasBindingSignature(bindingContext.hasBindingSignature());
        decodedCtx.getSAMLBindingContext().setIntendedDestinationEndpointURIRequired(bindingContext.isIntendedDestinationEndpointURIRequired());
        decodedCtx.getSAMLBindingContext().setRelayState(bindingContext.getRelayState());

        final AssertionConsumerService acsService = context.getSPAssertionConsumerService();
        decodedCtx.getSAMLEndpointContext().setEndpoint(acsService);

        final EntityDescriptor metadata = context.getSAMLPeerMetadataContext().getEntityDescriptor();
        if (metadata == null) {
            throw new SAMLException("IDP Metadata cannot be null");
        }

        decodedCtx.getSAMLPeerEntityContext().setEntityId(metadata.getEntityID());

        decodedCtx.getSAMLSelfEntityContext().setEntityId(context.getSAMLSelfEntityContext().getEntityId());
        decodedCtx.getSAMLSelfEndpointContext().setEndpoint(context.getSAMLSelfEndpointContext().getEndpoint());
        decodedCtx.getSAMLSelfEntityContext().setRole(context.getSAMLSelfEntityContext().getRole());

        decodedCtx.getProfileRequestContext().setProfileId(SAML2_WEBSSO_PROFILE_URI);

        return this.validator.validate(decodedCtx);
    }
}
