/*
 * Copyright 2010, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.action;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Out;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.framework.EntityNotFoundException;
import org.jboss.seam.log.Log;
import org.jboss.seam.security.management.JpaIdentityStore;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.zanata.common.ContentState;
import org.zanata.common.EntityStatus;
import org.zanata.common.TransUnitWords;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.process.CopyTransProcessHandle;
import org.zanata.security.ZanataIdentity;
import org.zanata.service.CopyTransService;
import org.zanata.service.LocaleService;

@Name("viewAllStatusAction")
@Scope(ScopeType.PAGE)
public class ViewAllStatusAction implements Serializable
{
   private static final long serialVersionUID = 1L;

   private static final PeriodFormatterBuilder PERIOD_FORMATTER_BUILDER =
         new PeriodFormatterBuilder()
               .appendDays().appendSuffix(" day", " days")
               .appendSeparator(", ")
               .appendHours().appendSuffix(" hour", " hours")
               .appendSeparator(", ")
               .appendMinutes().appendSuffix(" min", " mins");
   
   @Logger
   Log log;
   
   @In(required = false, value = JpaIdentityStore.AUTHENTICATED_USER)
   HAccount authenticatedAccount;
   
   @In
   ZanataIdentity identity;
   
   @In
   ProjectIterationDAO projectIterationDAO;
   
   @In
   LocaleService localeServiceImpl;

   @In
   CopyTransService copyTransServiceImpl;

   @In
   Map<String, String> messages;

   @In
   CopyTransManager copyTransManager;

   private String iterationSlug;
   
   private String projectSlug;
   
   private boolean showAllLocales = false;
   
   private HProjectIteration projectIteration;

   
   public static class Status implements Comparable<Status>
   {
      private String locale;
      private String nativeName;
      private TransUnitWords words;
      private int per;
      private boolean userInLanguageTeam;

      public Status(String locale, String nativeName, TransUnitWords words, int per, boolean userInLanguageTeam)
      {
         this.locale = locale;
         this.nativeName = nativeName;
         this.words = words;
         this.per = per;
         this.userInLanguageTeam = userInLanguageTeam;
      }

      public String getLocale()
      {
         return locale;
      }

      public String getNativeName()
      {
         return nativeName;
      }

      public TransUnitWords getWords()
      {
         return words;
      }

      public double getPer()
      {
         return per;
      }
      
      public boolean isUserInLanguageTeam()
      {
         return userInLanguageTeam;
      }

      @Override
      public int compareTo(Status o)
      {
         return ((Double) o.getPer()).compareTo((Double) this.getPer());
      }

   }

   public void setProjectSlug(String slug)
   {
      this.projectSlug = slug;
   }

   public String getProjectSlug()
   {
      return this.projectSlug;
   }

   public void setIterationSlug(String slug)
   {
      this.iterationSlug = slug;
   }

   public String getIterationSlug()
   {
      return this.iterationSlug;
   }

   public void validateIteration()
   {
      if( this.getProjectIteration() == null )
      {
         throw new EntityNotFoundException(this.iterationSlug, HProjectIteration.class);
      }
   }

   public List<Status> getAllStatus()
   {
      List<Status> result = new ArrayList<Status>();
      HProjectIteration iteration = projectIterationDAO.getBySlug(this.projectSlug, this.iterationSlug);
      Map<String, TransUnitWords> stats = projectIterationDAO.getAllWordStatsStatistics(iteration.getId());
      List<HLocale> locale = this.getDisplayLocales();
      Long total = projectIterationDAO.getTotalCountForIteration(iteration.getId());
      for (HLocale var : locale)
      {
         TransUnitWords words = stats.get(var.getLocaleId().getId());
         if (words == null)
         {
            words = new TransUnitWords();
            words.set(ContentState.New, total.intValue());

         }
         int per;
         if (total.intValue() == 0)
         {
            per = 0;
         }
         else
         {
            per = (int) Math.ceil(100 * words.getApproved() / words.getTotal());

         }
         boolean isMember = authenticatedAccount != null ? authenticatedAccount.getPerson().isMember(var) : false;
         
         Status op = new Status(var.getLocaleId().getId(), var.retrieveNativeName(), words, per, isMember);
         result.add(op);
      }
      Collections.sort(result);
      return result;
   }


   public boolean getShowAllLocales()
   {
      return showAllLocales;
   }

   public void setShowAllLocales(boolean showAllLocales)
   {
      this.showAllLocales = showAllLocales;
   }
   
   public HProjectIteration getProjectIteration()
   {
      if( this.projectIteration == null )
      {
         this.projectIteration = projectIterationDAO.getBySlug(projectSlug, iterationSlug);
      }
      return this.projectIteration;
   }

   public HProject getProject()
   {
      return this.getProjectIteration().getProject();
   }
   
   public boolean isIterationReadOnly()
   {
      return this.getProjectIteration().getProject().getStatus() == EntityStatus.READONLY || 
             this.getProjectIteration().getStatus() == EntityStatus.READONLY;
   }
   
   public boolean isIterationObsolete()
   {
      return this.getProjectIteration().getProject().getStatus() == EntityStatus.OBSOLETE || 
             this.getProjectIteration().getStatus() == EntityStatus.OBSOLETE;
   }
   
   public boolean isUserAllowedToTranslate(String localeId)
   {
      return !isIterationReadOnly() && !isIterationObsolete() 
             && identity.hasPermission("add-translation", getProject(), localeServiceImpl.getByLocaleId(localeId));
   }

   public boolean isCopyTransRunning()
   {
      return copyTransManager.isCopyTransRunning( getProjectIteration() );
   }

   public void startCopyTrans()
   {
      if( isCopyTransRunning() )
      {
         FacesMessages.instance().add("Someone else already started a translation copy for this version.");
         return;
      }
      else if( getProjectIteration().getDocuments().size() <= 0 )
      {
         FacesMessages.instance().add("There are no documents in this project version.");
         return;
      }

      copyTransManager.startCopyTrans( getProjectIteration() );
   }

   public void cancelCopyTrans()
   {
      if( isCopyTransRunning() )
      {
         copyTransManager.cancelCopyTrans( getProjectIteration() );
      }
   }

   public int getCopyTransProgress()
   {
      CopyTransProcessHandle handle = copyTransManager.getCopyTransProcessHandle(getProjectIteration());
      if( handle != null )
      {
         return handle.getCurrentProgress();
      }
      else
      {
         return Integer.MAX_VALUE; // Return the maximum so that the progress bar stops polling
      }
   }

   public int getCopyTransMaxProgress()
   {
      CopyTransProcessHandle handle = copyTransManager.getCopyTransProcessHandle(getProjectIteration());
      if( handle != null )
      {
         return handle.getMaxProgress();
      }
      else
      {
         return 1;
      }
   }

   public String getCopyTransStartTime()
   {
      CopyTransProcessHandle handle = copyTransManager.getCopyTransProcessHandle(getProjectIteration());
      long durationSinceStart = 0;
      if( handle.isStarted() )
      {
         durationSinceStart = (System.currentTimeMillis() - handle.getStartTime());
      }

      return formatTimePeriod( durationSinceStart );
   }

   public String getCopyTransEstimatedTimeLeft()
   {
      CopyTransProcessHandle handle = copyTransManager.getCopyTransProcessHandle(getProjectIteration());
      return formatTimePeriod(handle.getEstimatedTimeRemaining());
   }

   public String getCopyTransStatusMessage()
   {
      if( !isCopyTransRunning() )
      {
         CopyTransProcessHandle recentProcessHandle = copyTransManager.getMostRecentlyFinished( getProjectIteration() );
         StringBuilder message = new StringBuilder("Last Translation copy ");

         if( recentProcessHandle == null )
         {
            return null;
         }

         // cancelled
         if( recentProcessHandle.getCancelledBy() != null )
         {
            message.append("cancelled by ");

            // ... by the same user
            if( recentProcessHandle.getCancelledBy().equals( identity.getCredentials().getUsername() ) )
            {
               message.append("you ");
            }
            // .. by another user
            else
            {
               message.append( recentProcessHandle.getCancelledBy() ).append(" ");
            }

            // when was it done
            message.append(formatTimePeriod( System.currentTimeMillis() - recentProcessHandle.getCancelledTime() ))
                  .append(" ago.");
         }
         // completed
         else
         {
            message.append("completed by ");

            // ... by the same user
            if( recentProcessHandle.getTriggeredBy().equals( identity.getCredentials().getUsername() ) )
            {
               message.append("you ");
            }
            // .. by another user
            else
            {
               message.append( recentProcessHandle.getTriggeredBy() ).append(" ");
            }

            // when was it done
            message.append(formatTimePeriod( System.currentTimeMillis() - recentProcessHandle.getFinishTime() ))
                  .append(" ago.");
         }

         return message.toString();
      }
      return null;
   }

   public CopyTransProcessHandle getCopyTransProcessHandle()
   {
      return copyTransManager.getCopyTransProcessHandle( getProjectIteration() );
   }

   private String formatTimePeriod( long durationInMillis )
   {
      PeriodFormatter formatter = PERIOD_FORMATTER_BUILDER.toFormatter();
      CopyTransProcessHandle handle = copyTransManager.getCopyTransProcessHandle(getProjectIteration());
      Period period = new Period( durationInMillis );

      if( period.toStandardMinutes().getMinutes() <= 0 )
      {
         return "less than a minute"; // TODO Localize
      }
      else
      {
         return formatter.print( period.normalizedStandard() );
      }
   }
   
   private List<HLocale> getDisplayLocales()
   {
      if( this.showAllLocales || authenticatedAccount == null )
      {
         return localeServiceImpl.getSupportedLangugeByProjectIteration(this.projectSlug, this.iterationSlug);
      }
      else
      {
         return localeServiceImpl.getTranslation(projectSlug, iterationSlug, authenticatedAccount.getUsername());
      }
   }


}
