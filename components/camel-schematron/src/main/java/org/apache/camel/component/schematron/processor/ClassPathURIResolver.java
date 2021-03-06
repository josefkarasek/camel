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
package org.apache.camel.component.schematron.processor;

import java.io.File;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

/**
 * Class path resolver for schematron templates
 */
public class ClassPathURIResolver implements URIResolver {

    private final String rulesDir;

    /**
     * Constructor setter for rules directory path.
     */
    public ClassPathURIResolver(final String rulesDir) {
        this.rulesDir = rulesDir;
    }

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        return new StreamSource(ClassPathURIResolver.class.getClassLoader()
                .getResourceAsStream(rulesDir.concat(File.separator).concat(href)));
    }
}
