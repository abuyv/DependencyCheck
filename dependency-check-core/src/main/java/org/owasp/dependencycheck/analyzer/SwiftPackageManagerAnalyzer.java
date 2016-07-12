/*
 * This file is part of dependency-check-core.
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
 *
 * Copyright (c) 2015 Bianca Jiang. All Rights Reserved.
 */
package org.owasp.dependencycheck.analyzer;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.EvidenceCollection;
import org.owasp.dependencycheck.utils.FileFilterBuilder;
import org.owasp.dependencycheck.utils.Settings;

/**
 * @author Bianca Xue Jiang
 *
 */
public class SwiftPackageManagerAnalyzer extends AbstractFileTypeAnalyzer {

    /**
     * The name of the analyzer.
     */
    private static final String ANALYZER_NAME = "SWIFT Package Manager Analyzer";

    /**
     * The phase that this analyzer is intended to run in.
     */
    private static final AnalysisPhase ANALYSIS_PHASE = AnalysisPhase.INFORMATION_COLLECTION;

    /**
     * The file name to scan.
     */
    public static final String SPM_FILE_NAME = "Package.swift";
    
    /**
     * Filter that detects files named "package.json".
     */
    private static final FileFilter SPM_FILE_FILTER = FileFilterBuilder.newInstance().addFilenames(SPM_FILE_NAME).build();

    /**
     * The capture group #1 is the block variable.  
     * e.g. 
     * "import PackageDescription
     * let package = Package(
     *     name: "Gloss"
     *     )"
     */
    private static final Pattern SPM_BLOCK_PATTERN
            = Pattern.compile("let[^=]+=\\s*Package\\s*\\(\\s*([^)]*)\\s*\\)", Pattern.DOTALL);
    
    /**
     * Returns the FileFilter
     *
     * @return the FileFilter
     */
    @Override
    protected FileFilter getFileFilter() {
        return SPM_FILE_FILTER;
    }

    @Override
    protected void initializeFileTypeAnalyzer() {
        // NO-OP
    }

    /**
     * Returns the name of the analyzer.
     *
     * @return the name of the analyzer.
     */
    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    /**
     * Returns the phase that the analyzer is intended to run in.
     *
     * @return the phase that the analyzer is intended to run in.
     */
    @Override
    public AnalysisPhase getAnalysisPhase() {
        return ANALYSIS_PHASE;
    }

    /**
     * Returns the key used in the properties file to reference the analyzer's enabled property.
     *
     * @return the analyzer's enabled property setting key
     */
    @Override
    protected String getAnalyzerEnabledSettingKey() {
        return Settings.KEYS.ANALYZER_SWIFT_PACKAGE_MANAGER_ENABLED;
    }

    @Override
    protected void analyzeFileType(Dependency dependency, Engine engine)
            throws AnalysisException {
    	
    	String contents;
        try {
            contents = FileUtils.readFileToString(dependency.getActualFile(), Charset.defaultCharset());
        } catch (IOException e) {
            throw new AnalysisException(
                    "Problem occurred while reading dependency file.", e);
        }
        final Matcher matcher = SPM_BLOCK_PATTERN.matcher(contents);
        if (matcher.find()) {
            contents = contents.substring(matcher.end());
            final String packageDescription = matcher.group(1);
            if(packageDescription.isEmpty())
            	return;

            final EvidenceCollection product = dependency.getProductEvidence();
            final EvidenceCollection vendor = dependency.getVendorEvidence();
            
            //SPM is currently under development for SWIFT 3. Its current metadata includes package name and dependencies.
            //Future interesting metadata: version, license, homepage, author, summary, etc.
            final String name = addStringEvidence(product, packageDescription, "name", "name", Confidence.HIGHEST);
            if (!name.isEmpty()) {
                vendor.addEvidence(SPM_FILE_NAME, "name_project", name, Confidence.HIGHEST);
            }
        }
        setPackagePath(dependency);
    }
    
    private String addStringEvidence(EvidenceCollection evidences,
            String packageDescription, String field, String fieldPattern, Confidence confidence) {
        String value = "";
        
    	final Matcher matcher = Pattern.compile(
                String.format("%s *:\\s*\"([^\"]*)", fieldPattern), Pattern.DOTALL).matcher(packageDescription);
    	if(matcher.find()) {
    		value = matcher.group(1);
    	}
    	
    	if(value != null) {
    		value = value.trim();
    		if(value.length() > 0)
    			evidences.addEvidence (SPM_FILE_NAME, field, value, confidence);
    	}
    	
        return value;
    }

    private void setPackagePath(Dependency dep) {
    	File file = new File(dep.getFilePath());
    	String parent = file.getParent();
    	if(parent != null)
    		dep.setPackagePath(parent);
    }
}
