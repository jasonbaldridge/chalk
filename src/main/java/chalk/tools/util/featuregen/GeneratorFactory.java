/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chalk.tools.util.featuregen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import chalk.tools.dictionary.Dictionary;
import chalk.tools.util.InvalidFormatException;
import chalk.tools.util.ext.ExtensionLoader;

/**
 * Creates a set of feature generators based on a provided XML descriptor.
 *
 * Example of an XML descriptor:
 *
 * <generators>
 *   <charngram min = "2" max = "5"/>
 *   <definition/>
 *   <cache>
 *     <window prevLength = "3" nextLength = "3">
 *       <generators>
 *         <prevmap/>
 *         <sentence/>
 *         <tokenclass/>
 *         <tokenpattern/>
 *       </generators>
 *     </window>
 *   </cache>
 * </generators>
 *
 * Each XML element is mapped to a {@link GeneratorFactory.XmlFeatureGeneratorFactory} which
 * is responsible to process the element and create the specified
 * {@link AdaptiveFeatureGenerator}. Elements can contain other
 * elements in this case it is the responsibility of the mapped factory to process
 * the child elements correctly. In some factories this leads to recursive
 * calls the 
 * {@link GeneratorFactory.XmlFeatureGeneratorFactory#create(Element, FeatureGeneratorResourceProvider)}
 * method.
 *
 * In the example above the generators element is mapped to the
 * {@link GeneratorFactory.AggregatedFeatureGeneratorFactory} which then
 * creates all the aggregated {@link AdaptiveFeatureGenerator}s to
 * accomplish this it evaluates the mapping with the same mechanism
 * and gives the child element to the corresponding factories. All
 * created generators are added to a new instance of the
 * {@link AggregatedFeatureGenerator} which is then returned.
 */
public class GeneratorFactory {

  /**
   * The {@link XmlFeatureGeneratorFactory} is responsible to construct
   * an {@link AdaptiveFeatureGenerator} from an given XML {@link Element}
   * which contains all necessary configuration if any.
   */
  static interface XmlFeatureGeneratorFactory {

    /**
     * Creates an {@link AdaptiveFeatureGenerator} from a the describing
     * XML element.
     *
     * @param generatorElement the element which contains the configuration
     * @param resourceManager the resource manager which could be used
     *     to access referenced resources
     *
     * @return the configured {@link AdaptiveFeatureGenerator}
     */
    AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException;
  }

  /**
   * @see AggregatedFeatureGenerator
   */
  static class AggregatedFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager)  throws InvalidFormatException {

      Collection<AdaptiveFeatureGenerator> aggregatedGenerators =
          new LinkedList<AdaptiveFeatureGenerator>();

      NodeList childNodes = generatorElement.getChildNodes();

      for (int i = 0; i < childNodes.getLength(); i++) {
        Node childNode = childNodes.item(i);

        if (childNode instanceof Element) {
          Element aggregatedGeneratorElement = (Element) childNode;

          aggregatedGenerators.add(
              GeneratorFactory.createGenerator(aggregatedGeneratorElement, resourceManager));
        }
      }

      return new AggregatedFeatureGenerator(aggregatedGenerators.toArray(
              new AdaptiveFeatureGenerator[aggregatedGenerators.size()]));
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("generators", new AggregatedFeatureGeneratorFactory());
    }
  }

  /**
   * @see CachedFeatureGenerator
   */
  static class CachedFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    private CachedFeatureGeneratorFactory() {
    }

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

      Element cachedGeneratorElement = null;

      NodeList kids = generatorElement.getChildNodes();

      for (int i = 0; i < kids.getLength(); i++) {
        Node childNode = kids.item(i);

        if (childNode instanceof Element) {
          cachedGeneratorElement = (Element) childNode;
          break;
        }
      }

      if (cachedGeneratorElement == null) {
        throw new InvalidFormatException("Could not find containing generator element!");
      }

      AdaptiveFeatureGenerator cachedGenerator =
          GeneratorFactory.createGenerator(cachedGeneratorElement, resourceManager);

      return new CachedFeatureGenerator(cachedGenerator);
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("cache", new CachedFeatureGeneratorFactory());
    }
  }

  /**
   * @see CharacterNgramFeatureGenerator
   */
  static class CharacterNgramFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

      String minString = generatorElement.getAttribute("min");

      int min;

      try {
        min = Integer.parseInt(minString);
      } catch (NumberFormatException e) {
        throw new InvalidFormatException("min attribute '" + minString + "' is not a number!", e);
      }

      String maxString = generatorElement.getAttribute("max");

      int max;

      try {
        max = Integer.parseInt(maxString);
      } catch (NumberFormatException e) {
        throw new InvalidFormatException("max attribute '" + maxString + "' is not a number!", e);
      }

      return new CharacterNgramFeatureGenerator(min, max);
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("charngram", new CharacterNgramFeatureGeneratorFactory());
    }
  }

  /**
   * @see DefinitionFeatureGenerator
   */
  static class DefinitionFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    private static final String ELEMENT_NAME = "definition";

    private DefinitionFeatureGeneratorFactory() {
    }

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {
      return new OutcomePriorFeatureGenerator();
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put(ELEMENT_NAME, new DefinitionFeatureGeneratorFactory());
    }
  }

  /**
   * @see DictionaryFeatureGenerator
   */
  static class DictionaryFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {
      
      String dictResourceKey = generatorElement.getAttribute("dict");
      
      Object dictResource = resourceManager.getResource(dictResourceKey);
      
      if (!(dictResource instanceof Dictionary)) {
        throw new InvalidFormatException("No dictionary resource for key: " + dictResourceKey);
      }

      String prefix = generatorElement.getAttribute("prefix");
      
      return new DictionaryFeatureGenerator(prefix, (Dictionary) dictResource);
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("dictionary", new DictionaryFeatureGeneratorFactory());
    }
  }

  /**
   * @see PreviousMapFeatureGenerator
   */
  static class PreviousMapFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      return new PreviousMapFeatureGenerator();
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("prevmap", new PreviousMapFeatureGeneratorFactory());
    }
  }

  // TODO: Add parameters ... 
  
  /**
   * @see SentenceFeatureGenerator
   */
  static class SentenceFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      
      String beginFeatureString = generatorElement.getAttribute("begin");
      
      boolean beginFeature = true;
      if (beginFeatureString.length() != 0)
        beginFeature = Boolean.parseBoolean(beginFeatureString);
        
      String endFeatureString = generatorElement.getAttribute("end");
      boolean endFeature = true;
      if (endFeatureString.length() != 0)
        endFeature = Boolean.parseBoolean(endFeatureString);
      
      return new SentenceFeatureGenerator(beginFeature, endFeature);
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("sentence", new SentenceFeatureGeneratorFactory());
    }
  }

  /**
   * @see TokenClassFeatureGenerator
   */
  static class TokenClassFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      // TODO: Make it configurable ...
      return new TokenClassFeatureGenerator(true);
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("tokenclass", new TokenClassFeatureGeneratorFactory());
    }
  }

  static class TokenFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      
      return new TokenFeatureGenerator();
    }
    
    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("token", new TokenFeatureGeneratorFactory());
    }
  }
  
  static class BigramNameFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {
    
    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      
      return new BigramNameFeatureGenerator();
    }
    
    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("bigram", new BigramNameFeatureGeneratorFactory());
    }
  }
  
  /**
   * @see TokenPatternFeatureGenerator
   */
  static class TokenPatternFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      return new TokenPatternFeatureGenerator();
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("tokenpattern", new TokenPatternFeatureGeneratorFactory());
    }
  }

  /**
   * @see WindowFeatureGenerator
   */
  static class WindowFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager)  throws InvalidFormatException {

      Element nestedGeneratorElement = null;

      NodeList kids = generatorElement.getChildNodes();

      for (int i = 0; i < kids.getLength(); i++) {
        Node childNode = kids.item(i);

        if (childNode instanceof Element) {
          nestedGeneratorElement = (Element) childNode;
          break;
        }
      }

      if (nestedGeneratorElement == null) {
        throw new InvalidFormatException("window feature generator must contain" +
        		" an aggregator element");
      }
      
      AdaptiveFeatureGenerator nestedGenerator = GeneratorFactory.createGenerator(nestedGeneratorElement, resourceManager);
      
      String prevLengthString = generatorElement.getAttribute("prevLength");

      int prevLength;

      try {
        prevLength = Integer.parseInt(prevLengthString);
      } catch (NumberFormatException e) {
        throw new InvalidFormatException("prevLength attribute '" + prevLengthString + "' is not a number!", e);
      }
      
      String nextLengthString = generatorElement.getAttribute("nextLength");

      int nextLength;

      try {
        nextLength = Integer.parseInt(nextLengthString);
      } catch (NumberFormatException e) {
        throw new InvalidFormatException("nextLength attribute '" + nextLengthString + "' is not a number!", e);
      }  
      
      return new WindowFeatureGenerator(nestedGenerator, prevLength, nextLength);
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("window", new WindowFeatureGeneratorFactory());
    }
  }

  /**
   * @see TokenPatternFeatureGenerator
   */
  static class PrefixFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      return new PrefixFeatureGenerator();
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("prefix", new PrefixFeatureGeneratorFactory());
    }
  }
  
  /**
   * @see TokenPatternFeatureGenerator
   */
  static class SuffixFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {
    
    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) {
      return new SuffixFeatureGenerator();
    }
    
    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("suffix", new SuffixFeatureGeneratorFactory());
    }
  }
  
  static class CustomFeatureGeneratorFactory implements XmlFeatureGeneratorFactory {

    public AdaptiveFeatureGenerator create(Element generatorElement,
        FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {
      
      String featureGeneratorClassName = generatorElement.getAttribute("class");
      
      AdaptiveFeatureGenerator generator = ExtensionLoader.instantiateExtension(AdaptiveFeatureGenerator.class,
          featureGeneratorClassName);
      
      return generator;
    }

    static void register(Map<String, XmlFeatureGeneratorFactory> factoryMap) {
      factoryMap.put("custom", new CustomFeatureGeneratorFactory());
    }
  }
  
  private static Map<String, XmlFeatureGeneratorFactory> factories =
      new HashMap<String, XmlFeatureGeneratorFactory>();

  static {
    AggregatedFeatureGeneratorFactory.register(factories);
    CachedFeatureGeneratorFactory.register(factories);
    CharacterNgramFeatureGeneratorFactory.register(factories);
    DefinitionFeatureGeneratorFactory.register(factories);
    DictionaryFeatureGeneratorFactory.register(factories);
    PreviousMapFeatureGeneratorFactory.register(factories);
    SentenceFeatureGeneratorFactory.register(factories);
    TokenClassFeatureGeneratorFactory.register(factories);
    TokenFeatureGeneratorFactory.register(factories);
    BigramNameFeatureGeneratorFactory.register(factories);
    TokenPatternFeatureGeneratorFactory.register(factories);
    PrefixFeatureGeneratorFactory.register(factories);
    SuffixFeatureGeneratorFactory.register(factories);
    WindowFeatureGeneratorFactory.register(factories);
    CustomFeatureGeneratorFactory.register(factories);
  }

  /**
   * Creates a {@link AdaptiveFeatureGenerator} for the provided element.
   * To accomplish this it looks up the corresponding factory by the
   * element tag name. The factory is then responsible for the creation
   * of the generator from the element.
   *
   * @param generatorElement
   * @param resourceManager
   *
   * @return
   */
  static AdaptiveFeatureGenerator createGenerator(Element generatorElement,
      FeatureGeneratorResourceProvider resourceManager) throws InvalidFormatException {

    String elementName = generatorElement.getTagName();
    
    XmlFeatureGeneratorFactory generatorFactory = factories.get(elementName);

    if (generatorFactory == null) {
      throw new InvalidFormatException("Unexpected element: " + elementName);
    }
    
    return generatorFactory.create(generatorElement, resourceManager);
  }

  /**
   * Creates an {@link AdaptiveFeatureGenerator} from an provided XML descriptor.
   *
   * Usually this XML descriptor contains a set of nested feature generators
   * which are then used to generate the features by one of the opennlp
   * components.
   *
   * @param xmlDescriptorIn the {@link InputStream} from which the descriptor
   * is read, the stream remains open and must be closed by the caller.
   *
   * @param resourceManager the resource manager which is used to resolve resources
   * referenced by a key in the descriptor
   *
   * @return created feature generators
   *
   * @throws IOException if an error occurs during reading from the descriptor
   *     {@link InputStream}
   */
  public static AdaptiveFeatureGenerator create(InputStream xmlDescriptorIn,
      FeatureGeneratorResourceProvider resourceManager) throws IOException, InvalidFormatException {

    DocumentBuilderFactory documentBuilderFacoty = DocumentBuilderFactory.newInstance();

    DocumentBuilder documentBuilder;

    try {
      documentBuilder = documentBuilderFacoty.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }

    org.w3c.dom.Document xmlDescriptorDOM;

    try {
      xmlDescriptorDOM = documentBuilder.parse(xmlDescriptorIn);
    } catch (SAXException e) {
      throw new InvalidFormatException("Descriptor is not valid XML!", e);
    }

    Element generatorElement = xmlDescriptorDOM.getDocumentElement();

    return createGenerator(generatorElement, resourceManager);
  }
}
