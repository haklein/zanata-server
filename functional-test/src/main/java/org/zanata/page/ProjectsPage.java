/*
 * Copyright 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.zanata.page;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import com.google.common.base.Function;
import com.google.common.base.Predicate;

public class ProjectsPage extends AbstractPage
{
   public ProjectsPage(final WebDriver driver)
   {
      super(driver);
   }

   public CreateProjectPage clickOnCreateProjectLink()
   {
      WebElement createProjectActionLink = waitForTenSec().until(new Function<WebDriver, WebElement>()
      {
         @Override
         public WebElement apply(WebDriver driver)
         {
            return driver.findElement(By.linkText("Create project"));
         }
      });
      createProjectActionLink.click();
      return new CreateProjectPage(getDriver());
   }

   public ProjectPage goToProject(String projectName)
   {
      //TODO this can't handle project on different page
      WebElement link = getDriver().findElement(By.linkText(projectName));
      link.click();
      return new ProjectPage(getDriver());
   }
}