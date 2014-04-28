package org.apache.lucene.analysis.tr;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.zemberek.erisim.Zemberek;
import net.zemberek.islemler.KelimeKokFrekansKiyaslayici;
import net.zemberek.islemler.cozumleme.CozumlemeSeviyesi;
import net.zemberek.tr.yapi.TurkiyeTurkcesi;
import net.zemberek.yapi.Kelime;
import net.zemberek.yapi.Kok;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for {@link Zemberek2StemFilter}.
 */
public class Zemberek2StemFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {

    private final Zemberek zemberek = new Zemberek(new TurkiyeTurkcesi());
    private final String strategy;

    public Zemberek2StemFilterFactory(Map<String, String> args) {
        super(args);
        strategy = get(args, "strategy", "maxLength");
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }

        System.out.println("zemberek2 factory ctor called...");
    }

    @Override
    public void inform(ResourceLoader loader) throws IOException {
        System.out.println("zemberek2 inform called...");
    }


    @Override
    public TokenStream create(TokenStream input) {
        return new Zemberek2StemFilter(input);
    }

    /**
     * Stemmer based on <a href="https://code.google.com/p/zemberek">Zemberek2</a>
     */
    public final class Zemberek2StemFilter extends TokenFilter {

        private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
        private final KeywordAttribute keywordAttribute = addAttribute(KeywordAttribute.class);

        public Zemberek2StemFilter(TokenStream input) {
            super(input);
            System.out.println("zemberek2 ctor called...");
        }

        private String stem(String word, String aggregation) {

            Kelime[] cozumler = zemberek.kelimeCozumle(word, CozumlemeSeviyesi.TUM_KOKLER);

            if (cozumler.length == 0) return word;

            if ("first".equals(aggregation)) {
                return cozumler[0].kok().icerik();
            }

            switch (aggregation) {
                case "frequency":
                    Arrays.sort(cozumler, new KelimeKokFrekansKiyaslayici());
                    return cozumler[0].kok().icerik();
                case "maxLength":
                    Arrays.sort(cozumler, new RootLengthComparator());
                    return cozumler[0].kok().icerik();
                case "minLength":
                    Arrays.sort(cozumler, new RootLengthComparator());
                    return cozumler[cozumler.length - 1].kok().icerik();
                case "maxMorpheme":
                    Arrays.sort(cozumler, new RootMorphemeComparator());
                    return cozumler[0].kok().icerik();
                case "minMorpheme":
                    Arrays.sort(cozumler, new RootMorphemeComparator());
                    return cozumler[cozumler.length - 1].kok().icerik();
                default:
                    throw new RuntimeException("unknown strategy " + aggregation);
            }
        }

        @Override
        public boolean incrementToken() throws IOException {

            if (!input.incrementToken()) return false;
            if (keywordAttribute.isKeyword()) return true;

            /**
             *  copied from {@link org.apache.lucene.analysis.br.BrazilianStemFilter#incrementToken}
             */
            final String term = termAttribute.toString();
            final String s = stem(term, strategy);
            // If not stemmed, don't waste the time adjusting the token.
            if ((s != null) && !s.equals(term))
                termAttribute.setEmpty().append(s);

            return true;
        }
    }

    private class RootLengthComparator implements Comparator<Kelime> {
        @Override
        public int compare(Kelime o1, Kelime o2) {
            if (o1 == null || o2 == null) return -1;
            final Kok k1 = o1.kok();
            final Kok k2 = o2.kok();
            return k2.icerik().length() - k1.icerik().length();
        }
    }

    class RootMorphemeComparator implements Comparator<Kelime> {
        @Override
        public int compare(Kelime o1, Kelime o2) {
            if (o1 == null || o2 == null) return -1;
            return o2.ekler().size() - o1.ekler().size();
        }
    }

    public static void main(String[] args) throws IOException {

        StringReader reader = new StringReader("utansın ortaklar çekişme");

        Map<String, String> map = new HashMap<>();
        map.put("strategy", "first");

        Zemberek2StemFilterFactory factory = new Zemberek2StemFilterFactory(map);

        TokenStream stream = factory.create(new WhitespaceTokenizer(Version.LUCENE_48, reader));

        CharTermAttribute termAttribute = stream.getAttribute(CharTermAttribute.class);

        stream.reset();
        while (stream.incrementToken()) {

            String term = termAttribute.toString();
            System.out.println(term);
        }
    }
}
