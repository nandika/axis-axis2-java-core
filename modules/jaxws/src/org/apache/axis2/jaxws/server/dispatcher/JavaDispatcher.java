/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.axis2.jaxws.server.dispatcher;

import org.apache.axis2.jaxws.core.MessageContext;
import org.apache.axis2.jaxws.server.EndpointCallback;
import org.apache.axis2.jaxws.server.EndpointInvocationContext;
import org.apache.axis2.jaxws.utility.ClassUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
/**
 * JavaDispatcher is an abstract class that can be extended to implement an EndpointDispatcher to a
 * Java object.
 */
public abstract class JavaDispatcher implements EndpointDispatcher {

    private static final Log log = LogFactory.getLog(JavaDispatcher.class);

    protected Class serviceImplClass;
    protected Object serviceInstance;

    protected JavaDispatcher(Class impl, Object serviceInstance) {
        this.serviceImplClass = impl;
        this.serviceInstance = serviceInstance;
    }

    public abstract MessageContext invoke(MessageContext request) throws Exception;
    
    public abstract void invokeOneWay(MessageContext request);
    
    public abstract void invokeAsync(MessageContext request, EndpointCallback callback);

    protected abstract MessageContext createResponse(MessageContext request, Object[] input, Object output);
    
    protected abstract MessageContext createFaultResponse(MessageContext request, Throwable fault);
    
    
    public Class getServiceImplementationClass() {
        return serviceImplClass;
    }
    
    protected Object invokeTargetOperation(Method method, Object[] params) throws Exception {
        Object output = null;
        try {
            output = method.invoke(serviceInstance, params);
        } catch (Exception t) {
            if (log.isDebugEnabled()) {
                log.debug("Exception invoking a method of " + serviceImplClass.toString()
                        + " of instance " + serviceInstance.toString());
                log.debug("Exception type thrown: " + t.getClass().getName());
                log.debug("Method = " + method.toGenericString());
                for (int i = 0; i < params.length; i++) {
                    String value =
                            (params[i] == null) ? "null"
                                    : params[i].getClass().toString();
                    log.debug(" Argument[" + i + "] is " + value);
                }
            }
            
            throw t;
        }
        
        return output;
    }
    
    
    protected class AsyncInvocationWorker implements Callable {
        
        private Method method;
        private Object[] params;
        private ClassLoader classLoader;
        private EndpointInvocationContext eic;
        
        public AsyncInvocationWorker(Method m, Object[] p, ClassLoader cl, EndpointInvocationContext ctx) {
            method = m;
            params = p;
            classLoader = cl;
            eic = ctx;
        }
        
        public Object call() throws Exception {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Invoking target endpoint via the async worker.");
                }
                
                // Set the proper class loader so that we can properly marshall the
                // outbound response.
                ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader != null) {
                    Thread.currentThread().setContextClassLoader(classLoader);
                    if (log.isDebugEnabled()) {
                        log.debug("Context ClassLoader set to:" + classLoader);
                    }
                }
                
                // We have the method that is going to be invoked and the parameter data to invoke it 
                // with, so just invoke the operation.
                Object output = null;
                boolean faultThrown = false;
                Throwable fault = null;
                try {
                    output = invokeTargetOperation(method, params);
                } 
                catch (Exception e) {
                    fault = ClassUtils.getRootCause(e);
                    faultThrown = true;
                }
                
                // If this is a one way invocation, we are done and just need to return.
                if (eic.isOneWay()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Invocation pattern was one way, work complete.");
                        return null;
                    }
                }
                
                // Create the response MessageContext
                MessageContext request = eic.getRequestMessageContext();
                MessageContext response = null;
                if (faultThrown) {
                    // If a fault was thrown, we need to create a slightly different
                    // MessageContext, than in the response path.
                    response = createFaultResponse(request, fault);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Async invocation of the endpoint was successful.  Creating response message.");
                    }
                    response = createResponse(request, params, output);
                }

                EndpointInvocationContext eic = null;
                if (request.getInvocationContext() != null) {
                    eic = (EndpointInvocationContext) request.getInvocationContext();
                    eic.setResponseMessageContext(response);                
                }
                
                EndpointCallback callback = eic.getCallback();
                boolean handleFault = response.getMessage().isFault();
                if (!handleFault) {
                    if (log.isDebugEnabled()) {
                        log.debug("No fault detected in response message, sending back application response.");
                    }
                    callback.handleResponse(eic);
                }
                else {
                    if (log.isDebugEnabled()) {
                        log.debug("A fault was detected.  Sending back a fault response.");
                    }
                    callback.handleFaultResponse(eic);
                }
                
                // Set the thread's ClassLoader back to what it originally was.
                Thread.currentThread().setContextClassLoader(currentLoader);
                
            } catch (Throwable e) {
                // Exceptions are swallowed, there is no reason to rethrow them
                log.error("AN UNEXPECTED ERROR OCCURRED IN THE ASYNC WORKER THREAD");
                log.error("Exception is:" + e);
            }

            return null;
        }
    }

}
