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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.BreakpointSupport;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.test.junit4.CamelRunner;
import org.apache.camel.test.junit4.annotation.ContextInject;
import org.apache.camel.test.junit4.annotation.CreateRouteBuilder;
import org.apache.camel.test.junit4.annotation.ProvidesBreakpoint;
import org.apache.camel.test.junit4.annotation.UseDebugger;
import org.junit.Test;
import org.junit.runner.RunWith;

@UseDebugger
@RunWith(CamelRunner.class)
public class RunnerDebugTest {

    @ContextInject
    public CamelContext context;
    
    @EndpointInject(uri = "mock:result")
    public MockEndpoint resultEndpoint;
    
    @Produce(uri = "direct:start")
    public ProducerTemplate template;

    @ProvidesBreakpoint
    public Breakpoint createBreakpoint() {
        return new TestBreakpoint();
    }
    
    @Test
    public void testProvidesBreakpoint() {
        assertNotNull(context.getDebugger());
        
        template.sendBody("body");
        
        assertNotNull(context.getDebugger());
        assertNotNull(context.getDebugger().getBreakpoints());
        assertEquals(1, context.getDebugger().getBreakpoints().size());
        
        assertTrue(context.getDebugger().getBreakpoints().get(0) instanceof TestBreakpoint);
        assertTrue(((TestBreakpoint) context.getDebugger().getBreakpoints().get(0)).isBreakpointHit());
    }
    
    @CreateRouteBuilder
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }
    
    private static final class TestBreakpoint extends BreakpointSupport {
        
        private boolean breakpointHit;

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition<?> definition) {
            breakpointHit = true;
        }

        public boolean isBreakpointHit() {
            return breakpointHit;
        }
    }
}