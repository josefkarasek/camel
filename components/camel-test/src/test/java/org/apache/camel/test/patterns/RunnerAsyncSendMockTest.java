/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.test.patterns;

import static org.junit.Assert.assertTrue;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.test.junit4.annotation.ContextInject;
import org.apache.camel.test.junit4.annotation.MockEndpoints;
import org.junit.Test;
import org.junit.runner.RunWith;

@MockEndpoints("seda*")
@RunWith(CamelRunner.class)
public class RunnerAsyncSendMockTest {
    
    @ContextInject
    public CamelContext context;

    @EndpointInject(uri = "mock:seda:start")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;
    
    @Test
    public void testmakeAsyncApiCall() {
        try {
            resultEndpoint.expectedHeaderReceived("username", "admin123");
            resultEndpoint.expectedBodiesReceived("Hello");
            DefaultExchange dfex = new DefaultExchange(context);
            dfex.getIn().setHeader("username", "admin123");
            dfex.getIn().setHeader("password", "admin");
            dfex.getIn().setBody("Hello");
            template.asyncSend("seda:start", dfex);
            resultEndpoint.assertIsSatisfied();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue("Failed to make async call to api", false);
        }
    }
}
