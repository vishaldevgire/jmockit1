/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.injection;

import java.io.*;
import java.lang.annotation.*;
import javax.persistence.*;
import javax.xml.parsers.*;

import static mockit.internal.expectations.injection.InjectionPoint.*;

import org.jetbrains.annotations.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

/**
 * Detects and resolves dependencies belonging to the {@code javax.persistence} API, namely {@code EntityManagerFactory}
 * and {@code EntityManager}.
 */
final class JPADependencies
{
   @Nullable
   static JPADependencies createIfAvailableInClasspath(@NotNull InjectionState injectionState)
   {
      return PERSISTENCE_CONTEXT_CLASS == null ? null : new JPADependencies(injectionState);
   }

   @Nullable
   static String getDependencyIdIfAvailable(@NotNull Annotation annotation)
   {
      Class<? extends Annotation> annotationType = annotation.annotationType();

      if (annotationType == PERSISTENCE_UNIT_CLASS) {
         return ((PersistenceUnit) annotation).unitName();
      }
      else if (annotationType == PERSISTENCE_CONTEXT_CLASS) {
         return  ((PersistenceContext) annotation).unitName();
      }

      return null;
   }

   @NotNull private final InjectionState injectionState;
   @Nullable private String defaultPersistenceUnitName;

   JPADependencies(@NotNull InjectionState injectionState) { this.injectionState = injectionState; }

   @Nullable
   Object newInstanceIfApplicable(@NotNull Class<?> dependencyType, @NotNull Object dependencyKey)
   {
      if (dependencyType == ENTITY_MANAGER_FACTORY_CLASS) {
         String persistenceUnitName;

         if (dependencyKey instanceof String) {
            persistenceUnitName = extractIdFromDependencyKey((String) dependencyKey);
         }
         else {
            persistenceUnitName = discoverNameOfDefaultPersistenceUnit();
         }

         EntityManagerFactory emFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
         return emFactory;
      }

      if (dependencyType == ENTITY_MANAGER_CLASS) {
         return findOrCreateEntityManager(dependencyKey);
      }

      return null;
   }

   @NotNull
   private static String extractIdFromDependencyKey(@NotNull String dependencyKey)
   {
      int p = dependencyKey.indexOf(':');
      return dependencyKey.substring(p + 1);
   }

   @NotNull
   private String discoverNameOfDefaultPersistenceUnit()
   {
      if (defaultPersistenceUnitName != null) {
         return defaultPersistenceUnitName;
      }

      defaultPersistenceUnitName = "<unknown>";
      InputStream xmlFile = getClass().getResourceAsStream("/META-INF/persistence.xml");

      if (xmlFile != null) {
         try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            parser.parse(xmlFile, new DefaultHandler() {
               @Override
               public void startElement(String uri, String localName, String qName, Attributes attributes)
               {
                  if ("persistence-unit".equals(qName)) {
                     defaultPersistenceUnitName = attributes.getValue("name");
                  }
               }
            });
            xmlFile.close();
         }
         catch (ParserConfigurationException ignore) {}
         catch (SAXException ignore) {}
         catch (IOException ignore) {}
      }

      return defaultPersistenceUnitName;
   }

   @Nullable
   private Object findOrCreateEntityManager(@NotNull Object dependencyKey)
   {
      String persistenceUnitName;
      Object emFactoryKey;

      if (dependencyKey instanceof String) {
         persistenceUnitName = extractIdFromDependencyKey((String) dependencyKey);
         emFactoryKey = EntityManagerFactory.class.getName() + ':' + persistenceUnitName;
      }
      else {
         persistenceUnitName = null;
         emFactoryKey = EntityManagerFactory.class;
      }

      EntityManagerFactory emFactory = injectionState.getInstantiatedDependency(emFactoryKey);

      if (emFactory == null) {
         if (persistenceUnitName == null) {
            persistenceUnitName = discoverNameOfDefaultPersistenceUnit();
         }

         emFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
         injectionState.saveInstantiatedDependency(emFactoryKey, emFactory);
      }

      return emFactory.createEntityManager();
   }
}
