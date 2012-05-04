/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.common.MergeType;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

public interface TranslationService
{
   TranslationResult translate(Long textFlowId, LocaleId localeId, ContentState contentState, List<String> targetContents);

   /**
    * Translates all text flows in a document.
    *
    * @param projectSlug The project to translate
    * @param iterationSlug The project iteration to translate
    * @param docId The document identifier to translate
    * @param locale The locale that the translations belong to
    * @param translations The translations to save to the document
    * @param extensions The extensions to use while translating
    * @param mergeType Indicates how to handle the translations. AUTO will merge the new translations with the provided
    *                  ones. IMPORT will overwrite all existing translations with the new ones.
    * @return A list of text flow targets that could not be matched to any text flows in the source document.
    */
   Collection<TextFlowTarget> translateAll(String projectSlug, String iterationSlug, String docId, LocaleId locale, TranslationsResource translations, Set<String> extensions,
                                          MergeType mergeType);

   public interface TranslationResult
   {
      HTextFlow getTextFlow();
      HTextFlowTarget getPreviousTextFlowTarget();
      HTextFlowTarget getNewTextFlowTarget();
   }
}