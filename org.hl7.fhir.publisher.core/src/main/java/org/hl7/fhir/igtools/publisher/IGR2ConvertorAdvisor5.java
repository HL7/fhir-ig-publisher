package org.hl7.fhir.igtools.publisher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.utilities.FhirPublication;

public class IGR2ConvertorAdvisor5 extends BaseAdvisor_10_50 {

    @Override
    public boolean ignoreEntry(@Nullable Bundle.BundleEntryComponent bundleEntryComponent, @Nonnull FhirPublication fhirPublication) {
        return false;
    }

    @Override
    public void handleCodeSystem(CodeSystem cs, ValueSet vs) {
        cs.setId(vs.getId());
    }

    @Override
    public CodeSystem getCodeSystem(ValueSet src) {
        return null;
    }

}
