/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.util.locale;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.*;
import static java.util.Locale.FilteringMode.*;
import static java.util.Locale.LanguageRange.*;
import java.util.Map;

/**
 * Implementation for BCP47 Locale matching
 *
 */
public final class LocaleMatcher {

    public static List<Locale> filter(List<LanguageRange> priorityList,
                                      Collection<Locale> locales,
                                      FilteringMode mode) {
        if (priorityList.isEmpty() || locales.isEmpty()) {
            return new ArrayList<>(); // need to return a empty mutable List
        }

        // Create a list of language tags to be matched.
        List<String> tags = new ArrayList<>();
        for (Locale locale : locales) {
            tags.add(locale.toLanguageTag());
        }

        // Filter language tags.
        List<String> filteredTags = filterTags(priorityList, tags, mode);

        // Create a list of matching locales.
        List<Locale> filteredLocales = new ArrayList<>(filteredTags.size());
        for (String tag : filteredTags) {
              filteredLocales.add(Locale.forLanguageTag(tag));
        }

        return filteredLocales;
    }

    public static List<String> filterTags(List<LanguageRange> priorityList,
                                          Collection<String> tags,
                                          FilteringMode mode) {
        if (priorityList.isEmpty() || tags.isEmpty()) {
            return new ArrayList<>(); // need to return a empty mutable List
        }

        ArrayList<LanguageRange> list;
        if (mode == EXTENDED_FILTERING) {
            return filterExtended(priorityList, tags);
        } else {
            list = new ArrayList<>();
            for (LanguageRange lr : priorityList) {
                String range = lr.getRange();
                if (range.startsWith("*-")
                    || range.indexOf("-*") != -1) { // Extended range
                    if (mode == AUTOSELECT_FILTERING) {
                        return filterExtended(priorityList, tags);
                    } else if (mode == MAP_EXTENDED_RANGES) {
                        if (range.charAt(0) == '*') {
                            range = "*";
                        } else {
                            range = range.replaceAll("-[*]", "");
                        }
                        list.add(new LanguageRange(range, lr.getWeight()));
                    } else if (mode == REJECT_EXTENDED_RANGES) {
                        throw new IllegalArgumentException("An extended range \""
                                      + range
                                      + "\" found in REJECT_EXTENDED_RANGES mode.");
                    }
                } else { // Basic range
                    list.add(lr);
                }
            }

            return filterBasic(list, tags);
        }
    }

    private static List<String> filterBasic(List<LanguageRange> priorityList,
                                            Collection<String> tags) {
        int splitIndex = splitRanges(priorityList);
        List<LanguageRange> nonZeroRanges;
        List<LanguageRange> zeroRanges;
        if (splitIndex != -1) {
            nonZeroRanges = priorityList.subList(0, splitIndex);
            zeroRanges = priorityList.subList(splitIndex, priorityList.size());
        } else {
            nonZeroRanges = priorityList;
            zeroRanges = List.of();
        }

        List<String> list = new ArrayList<>();
        for (LanguageRange lr : nonZeroRanges) {
            String range = lr.getRange();
            if (range.equals("*")) {
                tags = removeTagsMatchingBasicZeroRange(zeroRanges, tags);
                return new ArrayList<String>(tags);
            } else {
                for (String tag : tags) {
                    tag = tag.toLowerCase(Locale.ROOT);
                    if (tag.startsWith(range)) {
                        int len = range.length();
                        if ((tag.length() == len || tag.charAt(len) == '-')
                            && !list.contains(tag)
                            && !shouldIgnoreFilterBasicMatch(zeroRanges, tag)) {
                            list.add(tag);
                        }
                    }
                }
            }
        }

        return list;
    }

    /**
     * Removes the tag(s) which are falling in the basic exclusion range(s) i.e
     * range(s) with q=0 and returns the updated collection. If the basic
     * language ranges contains '*' as one of its non zero range then instead of
     * returning all the tags, remove those which are matching the range with
     * quality weight q=0.
     */
    private static Collection<String> removeTagsMatchingBasicZeroRange(
            List<LanguageRange> zeroRange, Collection<String> tags) {
        if (zeroRange.isEmpty()) {
            return tags;
        }

        List<String> matchingTags = new ArrayList<>();
        for (String tag : tags) {
            tag = tag.toLowerCase(Locale.ROOT);
            if (!shouldIgnoreFilterBasicMatch(zeroRange, tag)) {
                matchingTags.add(tag);
            }
        }

        return matchingTags;
    }

    /**
     * The tag which is falling in the basic exclusion range(s) should not
     * be considered as the matching tag. Ignores the tag matching with the
     * non-zero ranges, if the tag also matches with one of the basic exclusion
     * ranges i.e. range(s) having quality weight q=0
     */
    private static boolean shouldIgnoreFilterBasicMatch(
            List<LanguageRange> zeroRange, String tag) {
        if (zeroRange.isEmpty()) {
            return false;
        }

        for (LanguageRange lr : zeroRange) {
            String range = lr.getRange();
            if (range.equals("*")) {
                return true;
            }
            if (tag.startsWith(range)) {
                int len = range.length();
                if ((tag.length() == len || tag.charAt(len) == '-')) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> filterExtended(List<LanguageRange> priorityList,
                                               Collection<String> tags) {
        int splitIndex = splitRanges(priorityList);
        List<LanguageRange> nonZeroRanges;
        List<LanguageRange> zeroRanges;
        if (splitIndex != -1) {
            nonZeroRanges = priorityList.subList(0, splitIndex);
            zeroRanges = priorityList.subList(splitIndex, priorityList.size());
        } else {
            nonZeroRanges = priorityList;
            zeroRanges = List.of();
        }

        List<String> list = new ArrayList<>();
        for (LanguageRange lr : nonZeroRanges) {
            String range = lr.getRange();
            if (range.equals("*")) {
                tags = removeTagsMatchingExtendedZeroRange(zeroRanges, tags);
                return new ArrayList<String>(tags);
            }
            String[] rangeSubtags = range.split("-");
            for (String tag : tags) {
                tag = tag.toLowerCase(Locale.ROOT);
                String[] tagSubtags = tag.split("-");
                if (!rangeSubtags[0].equals(tagSubtags[0])
                    && !rangeSubtags[0].equals("*")) {
                    continue;
                }

                int rangeIndex = matchFilterExtendedSubtags(rangeSubtags,
                        tagSubtags);
                if (rangeSubtags.length == rangeIndex && !list.contains(tag)
                        && !shouldIgnoreFilterExtendedMatch(zeroRanges, tag)) {
                    list.add(tag);
                }
            }
        }

        return list;
    }

    /**
     * Removes the tag(s) which are falling in the extended exclusion range(s)
     * i.e range(s) with q=0 and returns the updated collection. If the extended
     * language ranges contains '*' as one of its non zero range then instead of
     * returning all the tags, remove those which are matching the range with
     * quality weight q=0.
     */
    private static Collection<String> removeTagsMatchingExtendedZeroRange(
            List<LanguageRange> zeroRange, Collection<String> tags) {
        if (zeroRange.isEmpty()) {
            return tags;
        }

        List<String> matchingTags = new ArrayList<>();
        for (String tag : tags) {
            tag = tag.toLowerCase(Locale.ROOT);
            if (!shouldIgnoreFilterExtendedMatch(zeroRange, tag)) {
                matchingTags.add(tag);
            }
        }

        return matchingTags;
    }

    /**
     * The tag which is falling in the extended exclusion range(s) should
     * not be considered as the matching tag. Ignores the tag matching with the
     * non zero range(s), if the tag also matches with one of the extended
     * exclusion range(s) i.e. range(s) having quality weight q=0
     */
    private static boolean shouldIgnoreFilterExtendedMatch(
            List<LanguageRange> zeroRange, String tag) {
        if (zeroRange.isEmpty()) {
            return false;
        }

        String[] tagSubtags = tag.split("-");
        for (LanguageRange lr : zeroRange) {
            String range = lr.getRange();
            if (range.equals("*")) {
                return true;
            }

            String[] rangeSubtags = range.split("-");

            if (!rangeSubtags[0].equals(tagSubtags[0])
                    && !rangeSubtags[0].equals("*")) {
                continue;
            }

            int rangeIndex = matchFilterExtendedSubtags(rangeSubtags,
                    tagSubtags);
            if (rangeSubtags.length == rangeIndex) {
                return true;
            }
        }

        return false;
    }

    private static int matchFilterExtendedSubtags(String[] rangeSubtags,
            String[] tagSubtags) {
        int rangeIndex = 1;
        int tagIndex = 1;

        while (rangeIndex < rangeSubtags.length
                && tagIndex < tagSubtags.length) {
            if (rangeSubtags[rangeIndex].equals("*")) {
                rangeIndex++;
            } else if (rangeSubtags[rangeIndex]
                    .equals(tagSubtags[tagIndex])) {
                rangeIndex++;
                tagIndex++;
            } else if (tagSubtags[tagIndex].length() == 1
                    && !tagSubtags[tagIndex].equals("*")) {
                break;
            } else {
                tagIndex++;
            }
        }
        return rangeIndex;
    }

    public static Locale lookup(List<LanguageRange> priorityList,
                                Collection<Locale> locales) {
        if (priorityList.isEmpty() || locales.isEmpty()) {
            return null;
        }

        // Create a list of language tags to be matched.
        List<String> tags = new ArrayList<>();
        for (Locale locale : locales) {
            tags.add(locale.toLanguageTag());
        }

        // Look up a language tags.
        String lookedUpTag = lookupTag(priorityList, tags);

        if (lookedUpTag == null) {
            return null;
        } else {
            return Locale.forLanguageTag(lookedUpTag);
        }
    }

    public static String lookupTag(List<LanguageRange> priorityList,
                                   Collection<String> tags) {
        if (priorityList.isEmpty() || tags.isEmpty()) {
            return null;
        }

        int splitIndex = splitRanges(priorityList);
        List<LanguageRange> nonZeroRanges;
        List<LanguageRange> zeroRanges;
        if (splitIndex != -1) {
            nonZeroRanges = priorityList.subList(0, splitIndex);
            zeroRanges = priorityList.subList(splitIndex, priorityList.size());
        } else {
            nonZeroRanges = priorityList;
            zeroRanges = List.of();
        }

        for (LanguageRange lr : nonZeroRanges) {
            String range = lr.getRange();

            // Special language range ("*") is ignored in lookup.
            if (range.equals("*")) {
                continue;
            }

            String rangeForRegex = range.replaceAll("\\x2A", "\\\\p{Alnum}*");
            while (rangeForRegex.length() > 0) {
                for (String tag : tags) {
                    tag = tag.toLowerCase(Locale.ROOT);
                    if (tag.matches(rangeForRegex)
                            && !shouldIgnoreLookupMatch(zeroRanges, tag)) {
                        return tag;
                    }
                }

                // Truncate from the end....
                rangeForRegex = truncateRange(rangeForRegex);
            }
        }

        return null;
    }

    /**
     * The tag which is falling in the exclusion range(s) should not be
     * considered as the matching tag. Ignores the tag matching with the
     * non zero range(s), if the tag also matches with one of the exclusion
     * range(s) i.e. range(s) having quality weight q=0.
     */
    private static boolean shouldIgnoreLookupMatch(List<LanguageRange> zeroRange,
            String tag) {
        for (LanguageRange lr : zeroRange) {
            String range = lr.getRange();

            // Special language range ("*") is ignored in lookup.
            if (range.equals("*")) {
                continue;
            }

            String rangeForRegex = range.replaceAll("\\x2A", "\\\\p{Alnum}*");
            while (rangeForRegex.length() > 0) {
                if (tag.matches(rangeForRegex)) {
                    return true;
                }
                // Truncate from the end....
                rangeForRegex = truncateRange(rangeForRegex);
            }
        }

        return false;
    }

    /* Truncate the range from end during the lookup match */
    private static String truncateRange(String rangeForRegex) {
        int index = rangeForRegex.lastIndexOf('-');
        if (index >= 0) {
            rangeForRegex = rangeForRegex.substring(0, index);

            // if range ends with an extension key, truncate it.
            index = rangeForRegex.lastIndexOf('-');
            if (index >= 0 && index == rangeForRegex.length() - 2) {
                rangeForRegex
                        = rangeForRegex.substring(0, rangeForRegex.length() - 2);
            }
        } else {
            rangeForRegex = "";
        }

        return rangeForRegex;
    }

    /* Returns the split index of the priority list, if it contains
     * language range(s) with quality weight as 0 i.e. q=0, else -1
     */
    private static int splitRanges(List<LanguageRange> priorityList) {
        int size = priorityList.size();
        for (int index = 0; index < size; index++) {
            LanguageRange range = priorityList.get(index);
            if (range.getWeight() == 0) {
                return index;
            }
        }

        return -1; // no q=0 range exists
    }

    public static List<LanguageRange> parse(String ranges) {
        ranges = ranges.replaceAll(" ", "").toLowerCase(Locale.ROOT);
        if (ranges.startsWith("accept-language:")) {
            ranges = ranges.substring(16); // delete unnecessary prefix
        }

        String[] langRanges = ranges.split(",");
        List<LanguageRange> list = new ArrayList<>(langRanges.length);
        List<String> tempList = new ArrayList<>();
        int numOfRanges = 0;

        for (String range : langRanges) {
            int index;
            String r;
            double w;

            if ((index = range.indexOf(";q=")) == -1) {
                r = range;
                w = MAX_WEIGHT;
            } else {
                r = range.substring(0, index);
                index += 3;
                try {
                    w = Double.parseDouble(range.substring(index));
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("weight=\""
                                  + range.substring(index)
                                  + "\" for language range \"" + r + "\"");
                }

                if (w < MIN_WEIGHT || w > MAX_WEIGHT) {
                    throw new IllegalArgumentException("weight=" + w
                                  + " for language range \"" + r
                                  + "\". It must be between " + MIN_WEIGHT
                                  + " and " + MAX_WEIGHT + ".");
                }
            }

            if (!tempList.contains(r)) {
                LanguageRange lr = new LanguageRange(r, w);
                index = numOfRanges;
                for (int j = 0; j < numOfRanges; j++) {
                    if (list.get(j).getWeight() < w) {
                        index = j;
                        break;
                    }
                }
                list.add(index, lr);
                numOfRanges++;
                tempList.add(r);

                // Check if the range has an equivalent using IANA LSR data.
                // If yes, add it to the User's Language Priority List as well.

                // aa-XX -> aa-YY
                String equivalent;
                if ((equivalent = getEquivalentForRegionAndVariant(r)) != null
                    && !tempList.contains(equivalent)) {
                    list.add(index+1, new LanguageRange(equivalent, w));
                    numOfRanges++;
                    tempList.add(equivalent);
                }

                String[] equivalents;
                if ((equivalents = getEquivalentsForLanguage(r)) != null) {
                    for (String equiv: equivalents) {
                        // aa-XX -> bb-XX(, cc-XX)
                        if (!tempList.contains(equiv)) {
                            list.add(index+1, new LanguageRange(equiv, w));
                            numOfRanges++;
                            tempList.add(equiv);
                        }

                        // bb-XX -> bb-YY(, cc-YY)
                        equivalent = getEquivalentForRegionAndVariant(equiv);
                        if (equivalent != null
                            && !tempList.contains(equivalent)) {
                            list.add(index+1, new LanguageRange(equivalent, w));
                            numOfRanges++;
                            tempList.add(equivalent);
                        }
                    }
                }
            }
        }

        return list;
    }

    private static String[] getEquivalentsForLanguage(String range) {
        String r = range;

        while (r.length() > 0) {
            if (LocaleEquivalentMaps.singleEquivMap.containsKey(r)) {
                String equiv = LocaleEquivalentMaps.singleEquivMap.get(r);
                // Return immediately for performance if the first matching
                // subtag is found.
                return new String[] {range.replaceFirst(r, equiv)};
            } else if (LocaleEquivalentMaps.multiEquivsMap.containsKey(r)) {
                String[] equivs = LocaleEquivalentMaps.multiEquivsMap.get(r);
                for (int i = 0; i < equivs.length; i++) {
                    equivs[i] = range.replaceFirst(r, equivs[i]);
                }
                return equivs;
            }

            // Truncate the last subtag simply.
            int index = r.lastIndexOf('-');
            if (index == -1) {
                break;
            }
            r = r.substring(0, index);
        }

        return null;
    }

    private static String getEquivalentForRegionAndVariant(String range) {
        int extensionKeyIndex = getExtentionKeyIndex(range);

        for (String subtag : LocaleEquivalentMaps.regionVariantEquivMap.keySet()) {
            int index;
            if ((index = range.indexOf(subtag)) != -1) {
                // Check if the matching text is a valid region or variant.
                if (extensionKeyIndex != Integer.MIN_VALUE
                    && index > extensionKeyIndex) {
                    continue;
                }

                int len = index + subtag.length();
                if (range.length() == len || range.charAt(len) == '-') {
                    return range.replaceFirst(subtag, LocaleEquivalentMaps.regionVariantEquivMap.get(subtag));
                }
            }
        }

        return null;
    }

    private static int getExtentionKeyIndex(String s) {
        char[] c = s.toCharArray();
        int index = Integer.MIN_VALUE;
        for (int i = 1; i < c.length; i++) {
            if (c[i] == '-') {
                if (i - index == 2) {
                    return index;
                } else {
                    index = i;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    public static List<LanguageRange> mapEquivalents(
                                          List<LanguageRange>priorityList,
                                          Map<String, List<String>> map) {
        if (priorityList.isEmpty()) {
            return new ArrayList<>(); // need to return a empty mutable List
        }
        if (map == null || map.isEmpty()) {
            return new ArrayList<LanguageRange>(priorityList);
        }

        // Create a map, key=originalKey.toLowerCaes(), value=originalKey
        Map<String, String> keyMap = new HashMap<>();
        for (String key : map.keySet()) {
            keyMap.put(key.toLowerCase(Locale.ROOT), key);
        }

        List<LanguageRange> list = new ArrayList<>();
        for (LanguageRange lr : priorityList) {
            String range = lr.getRange();
            String r = range;
            boolean hasEquivalent = false;

            while (r.length() > 0) {
                if (keyMap.containsKey(r)) {
                    hasEquivalent = true;
                    List<String> equivalents = map.get(keyMap.get(r));
                    if (equivalents != null) {
                        int len = r.length();
                        for (String equivalent : equivalents) {
                            list.add(new LanguageRange(equivalent.toLowerCase(Locale.ROOT)
                                     + range.substring(len),
                                     lr.getWeight()));
                        }
                    }
                    // Return immediately if the first matching subtag is found.
                    break;
                }

                // Truncate the last subtag simply.
                int index = r.lastIndexOf('-');
                if (index == -1) {
                    break;
                }
                r = r.substring(0, index);
            }

            if (!hasEquivalent) {
                list.add(lr);
            }
        }

        return list;
    }

    private LocaleMatcher() {}

}
