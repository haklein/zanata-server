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
package org.zanata.seam;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import org.apache.commons.lang.ArrayUtils;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.log.Logging;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helps with Auto-wiring of Seam components for integrated tests without the need for a full Seam
 * environment.
 * It's a singleton class that upon first use will change the way Seam's {@link org.jboss.seam.Component} class
 * works by returning its own auto-wired components.
 *
 * Supports components injected using:
 * {@link In}
 * {@link Logger}
 * {@link org.jboss.seam.Component#getInstance(String)} and similar methods...
 * and that have no-arg constructors
 *
 * @author Carlos Munoz <a href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
public class SeamAutowire
{
   private static final org.slf4j.Logger log = LoggerFactory.getLogger(SeamAutowire.class);

   private static SeamAutowire instance;

   private Map<String, Object> namedComponents = new HashMap<String, Object>();

   private Map<Class<?>, Class<?>> componentImpls = new HashMap<Class<?>, Class<?>>();

   //private Map<Class, Object> classComponents = new HashMap<Class, Object>();

   private boolean ignoreNonResolvable;


   protected SeamAutowire()
   {
   }

   /**
    * Initializes and returns the SeamAutowire instance.
    *
    * @return The Singleton instance of the SeamAutowire class.
    */
   public static final SeamAutowire instance()
   {
      if(instance == null)
      {
         instance = new SeamAutowire();
         instance.rewireSeamComponentClass();
      }
      return instance;
   }

   /**
    * Clears out any components and returns to it's initial value.
    */
   public SeamAutowire reset()
   {
      this.ignoreNonResolvable = false;
      this.namedComponents.clear();
      return this;
   }

   /**
    * Indicates a specific instance of a component to use.
    *
    * @param name The name of the component. When another component injects using <code>@In(value = "name")</code> or
    *             <code>@In varName</code>, the provided component will be used.
    * @param component The component instance to use under the provided name.
    */
   public SeamAutowire use(String name, Object component)
   {
      this.addComponentInstance(name, component);
      return this;
   }

   /**
    * Registers an implementation to use for components. This method is provided just in case some components are
    * inject via interfaces.
    *
    * @param cls The class to register.
    */
   public SeamAutowire useImpl(Class<?> cls)
   {
      if( cls.isInterface() )
      {
         throw new RuntimeException("Class " + cls.getName() + " is an interface.");
      }
      this.registerInterfaces(cls);

      return this;
   }

   /**
    * Indicates that a warning should be logged if for some reason a component cannot be resolved. Otherwise, an
    * exception will be thrown.
    */
   public SeamAutowire ignoreNonResolvable()
   {
      this.ignoreNonResolvable = true;
      return this;
   }

   /**
    * Returns a component by name.
    *
    * @param name Component's name.
    * @return The component registered under the provided name, or null if such a component has not been auto wired
    * or cannot be resolved otherwise.
    */
   public Object getComponent(String name)
   {
      if( this.namedComponents.containsKey(name) )
      {
         return this.namedComponents.get(name);
      }
      return null;
   }

   /**
    * Autowires and returns the component instance for the provided class.
    *
    * @param componentClass The component class to autowire.
    * @return The autowired component.
    */
   public <T> T autowire(Class<T> componentClass)
   {
      // Create a new component instance (must have a no-arg constructor per Seam spec)
      T component;

      // If the component type is an interface, try to find a declared implementation
      if( componentClass.isInterface() && this.componentImpls.containsKey(componentClass) )
      {
         componentClass = (Class<T>)this.componentImpls.get(componentClass);
      }

      try
      {
         component = componentClass.newInstance();
      }
      catch (InstantiationException e)
      {
         throw new RuntimeException("Could not auto-wire component of type " + componentClass.getName(), e);
      }
      catch (IllegalAccessException e)
      {
         throw new RuntimeException("Could not auto-wire component of type " + componentClass.getName(), e);
      }

      return this.autowire(component);
   }

   /**
    * Autowires a component instance. The provided instance of the component will be autowired instead of creating a
    * new one.
    *
    * @param component The component instance to autowire.
    * @param <T>
    * @return Returns component.
    */
   public <T> T autowire(T component)
   {
      Class<T> componentClass = (Class<T>)component.getClass();

      // Register all interfaces for this class
      this.registerInterfaces(componentClass);

      for( Field compField : getAllComponentFields(component) )
      {
         compField.setAccessible(true);

         // Other annotated component
         if( compField.getAnnotation(In.class) != null )
         {
            Object fieldVal = null;
            String compName = getComponentName(compField);
            Class<?> compType = compField.getType();

            // autowire the component if not done yet
            if( !namedComponents.containsKey(compName) )
            {
               try
               {
                  Object newComponent = autowire(compType);
                  this.addComponentInstance(compName, newComponent);
               }
               catch (RuntimeException e)
               {
                  if( ignoreNonResolvable )
                  {
                     log.warn("Could not resolve component of type: " + compType);
                  }
                  else
                  {
                     throw e;
                  }
               }
            }

            fieldVal = namedComponents.get( compName );

            try
            {
               compField.set( component, fieldVal);
            }
            catch (IllegalAccessException e)
            {
               throw new RuntimeException("Could not auto-wire field " + compField.getName() +
                     " in component of type " + componentClass.getName(), e);
            }
         }
         // Logs
         else if( compField.getAnnotation(Logger.class) != null )
         {
            try
            {
               compField.set( component, Logging.getLog(compField.getType()));
            }
            catch (IllegalAccessException e)
            {
               throw new RuntimeException("Could not auto-wire field " + compField.getName() +
                     " in component of type " + componentClass.getName(), e);
            }
         }
      }

      // Resolve injected components using methods
      for( Method compMethod : getAllComponentMethods(component) )
      {
         compMethod.setAccessible(true);

         // Other annotated component
         if( compMethod.getAnnotation(In.class) != null )
         {
            Object fieldVal = null;
            String compName = getComponentName(compMethod);
            Class<?> compType = compMethod.getParameterTypes()[0];

            // autowire the component if not done yet
            if( !namedComponents.containsKey(compName) )
            {
               try
               {
                  Object newComponent = autowire(compType);
                  this.addComponentInstance(compName, newComponent);
               }
               catch (RuntimeException e)
               {
                  if( ignoreNonResolvable )
                  {
                     log.warn("Could not resolve component of type: " + compType);
                  }
                  else
                  {
                     throw e;
                  }
               }
            }

            fieldVal = namedComponents.get(compName);

            try
            {
               compMethod.invoke(component, fieldVal);
            }
            catch (InvocationTargetException e)
            {
               throw new RuntimeException("Could not auto-wire field " + compMethod.getName() +
                     " in component of type " + componentClass.getName(), e);
            }
            catch (IllegalAccessException e)
            {
               throw new RuntimeException("Could not auto-wire field " + compMethod.getName() +
                     " in component of type " + componentClass.getName(), e);
            }
         }
         // Logs
         else if( compMethod.getAnnotation(Logger.class) != null )
         {
            try
            {
               compMethod.invoke( component, Logging.getLog(compMethod.getParameterTypes()[0]));
            }
            catch(InvocationTargetException e)
            {
               throw new RuntimeException("Could not auto-wire field " + compMethod.getName() +
                     " in component of type " + componentClass.getName(), e);
            }
            catch (IllegalAccessException e)
            {
               throw new RuntimeException("Could not auto-wire field " + compMethod.getName() +
                     " in component of type " + componentClass.getName(), e);
            }
         }
      }

      // call post constructor
      invokePostConstructMethod(component);

      return component;
   }

   private void addComponentInstance(String name, Object compInst)
   {
      this.namedComponents.put(name, compInst);
   }

   private void rewireSeamComponentClass()
   {
      try
      {
         ClassPool pool = ClassPool.getDefault();
         CtClass cls = pool.get("org.jboss.seam.Component");

         // Commonly used CtClasses
         final CtClass stringCls = pool.get("java.lang.String");
         final CtClass booleanCls = pool.get("boolean");
         final CtClass objectCls = pool.get("java.lang.Object");
         final CtClass scopeTypeCls = pool.get("org.jboss.seam.ScopeType");

         // Replace Component's method bodies with the ones in AutowireComponent
         CtMethod methodToReplace = cls.getDeclaredMethod("getInstance", new CtClass[]{stringCls, booleanCls, booleanCls});
         methodToReplace.setBody(
               pool.get(AutowireComponent.class.getName()).getDeclaredMethod("getInstance", new CtClass[]{stringCls, booleanCls, booleanCls}),
               null);

         methodToReplace = cls.getDeclaredMethod("getInstance", new CtClass[]{stringCls, scopeTypeCls, booleanCls, booleanCls});
         methodToReplace.setBody(
               pool.get(AutowireComponent.class.getName()).getDeclaredMethod("getInstance", new CtClass[]{stringCls, scopeTypeCls, booleanCls, booleanCls}),
               null);

         methodToReplace = cls.getDeclaredMethod("getInstance", new CtClass[]{stringCls, booleanCls, booleanCls, objectCls});
         methodToReplace.setBody(
               pool.get(AutowireComponent.class.getName()).getDeclaredMethod("getInstance", new CtClass[]{stringCls, booleanCls, booleanCls, objectCls}),
               null);

         cls.toClass();
      }
      catch (NotFoundException e)
      {
         throw new RuntimeException("Problem rewiring Seam's Component class", e);
      }
      catch (CannotCompileException e)
      {
         throw new RuntimeException("Problem rewiring Seam's Component class", e);
      }

   }

   private static Field[] getAllComponentFields(Object component)
   {
      Field[] fields = component.getClass().getDeclaredFields();
      Class<?> superClass = component.getClass().getSuperclass();

      while(superClass != null)
      {
         fields = (Field[])ArrayUtils.addAll(fields, superClass.getDeclaredFields());
         superClass = superClass.getSuperclass();
      }

      return fields;
   }

   private static Method[] getAllComponentMethods(Object component)
   {
      Method[] methods = component.getClass().getDeclaredMethods();
      Class<?> superClass = component.getClass().getSuperclass();

      while(superClass != null)
      {
         methods = (Method[])ArrayUtils.addAll(methods, superClass.getDeclaredMethods());
         superClass = superClass.getSuperclass();
      }

      return methods;
   }

   private static String getComponentName( Field field )
   {
      In inAnnot = field.getAnnotation(In.class);
      if( inAnnot != null )
      {
         if( inAnnot.value().trim().isEmpty() )
         {
            return field.getName();
         }
         else
         {
            return inAnnot.value();
         }
      }
      return null;
   }

   private static String getComponentName( Method method )
   {
      In inAnnot = method.getAnnotation(In.class);
      if( inAnnot != null )
      {
         if( inAnnot.value().trim().isEmpty() )
         {
            // assume it's a setter
            String name = method.getName().substring(3);
            name = name.substring(0,1).toLowerCase() + name.substring(1);
            return name;
         }
         else
         {
            return inAnnot.value();
         }
      }
      return null;
   }

   private void registerInterfaces(Class<?> cls)
   {
      if( !cls.isInterface() )
      {
         // register all interfaces registered by this component
         for (Class<?> iface : getAllInterfaces(cls))
         {
            this.componentImpls.put(iface, cls);
         }
      }
   }

   private static Set<Class<?>> getAllInterfaces(Class<?> cls)
   {
      Set<Class<?>> interfaces = new HashSet<Class<?>>();

      for( Class<?> superClass : cls.getInterfaces() )
      {
         interfaces.add(superClass);
         interfaces.addAll( getAllInterfaces(superClass) );
      }

      return interfaces;
   }

   private static void invokePostConstructMethod(Object component)
   {
      Class<?> compClass = component.getClass();

      for( Method m : compClass.getDeclaredMethods() )
      {
         // Call the first Post Constructor found. Per the spec, there should be only one
         if( m.getAnnotation(javax.annotation.PostConstruct.class) != null
             || m.getAnnotation(org.jboss.seam.annotations.intercept.PostConstruct.class) != null )
         {
            try
            {
               m.invoke(component, null); // there should be no params
            }
            catch (IllegalAccessException e)
            {
               throw new RuntimeException("Error invoking Post construct method in component of class: " + compClass.getName(), e);
            }
            catch (InvocationTargetException e)
            {
               throw new RuntimeException("Error invoking Post construct method in component of class: " + compClass.getName(), e);
            }
         }
      }
   }

}