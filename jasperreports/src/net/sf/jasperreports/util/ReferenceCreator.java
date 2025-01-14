package net.sf.jasperreports.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.sf.jasperreports.engine.JRRewindableDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

/**
 * This class is used to gather reference keys and their associated incremental reference numbers during report fill
 * time. Each reference number is associated to a String key. During collection, key/number pairs are added and the next
 * number is incremented. When finished, the reference data are returned as a JRDataSource or by direct retrieval by
 * reference key.
 */
public class ReferenceCreator {

    private static final String REFERENCE_KEY = "refKey";
    private static final String REFERENCE_TEXT = "refText";
    private static final String REFERENCE_NUMBER = "refNumber";
    private static final String STYLED_REFERENCE_NUMBER_FORMAT =
            " <sup><style forecolor='blue' isUnderline='true'>%s</style></sup>";

    private int currentReferenceNumber = 1;

    private final List<Map<String, String>> references = new ArrayList<>();
    private final Set<String> keysAdded = new HashSet<>();

    /**
     * Returns the reference number for the given key for use in assigning a reference number to a super-script text
     * field. Must be rendered at Report time or at least after the given reference key has been added.
     * @param referenceKey
     * @return
     * @throws RuntimeException if the given key is not found (ensures robust report configuration).
     */
    private String getReferenceNumberForKey(String referenceKey) {
        for (Map<String, String> reference : this.references) {
            if (referenceKey.equals(reference.get(REFERENCE_KEY))) {
                return reference.get(REFERENCE_NUMBER);
            }
        }
        throw new RuntimeException("Reference key \"" + referenceKey + "\" not found! Potential mis-match of key names, or" +
                "a call to getReferenceNumberForKey() when the text was empty - if the latter, try using " +
                "getStyledReferenceNumberTextForKey() instead.");
    }

    public String createReferenceNumberAnchorForKey(String referenceKey) {

        if (!keysAdded.contains(referenceKey)) {
            return null;
        }

        return createReferenceNumberAnchor(getReferenceNumberForKey(referenceKey));
    }

    /**
     * Creates an anchor to the lowest reference number for whichever of the reference keys is present in keys added.
     * @param referenceKeys
     * @return
     */
    public String createReferenceNumberAnchorForKeys(String... referenceKeys) {

        Set<String> addedKeys = filterKeysAdded(referenceKeys);
        if (addedKeys.isEmpty()) {
            return null;
        }

        String smallestReferenceNumber = addedKeys.stream().map(this::getReferenceNumberForKey)
                .map(Integer::parseInt).min(Integer::compare).map(String::valueOf)
                .orElseThrow(() -> new RuntimeException("Missing reference number for reference keys " +
                        String.join(", ", addedKeys)));

        return createReferenceNumberAnchor(smallestReferenceNumber);

    }

    public String createReferenceNumberAnchor(String value) {

        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Additional validation to align the method name with it's counterpart "...ForKey".
            throw new RuntimeException("Value is not numeric!");
        }

        return "ref_" + value;
    }

    /**
     * Returns the reference number for the given key as a string of super-script styled XML to be used in a text
     * element. The text element must have the attribute {@code markup="styled"}. If the reference key is not in the
     * list of reference keys added, for example due to the provided text being empty, then an empty string is returned.
     * @param referenceKey
     * @return
     */
    public String getStyledReferenceNumberTextForKey(String referenceKey) {

        if (!keysAdded.contains(referenceKey)) {
            return "";
        }

        String referenceNumberStr = getReferenceNumberForKey(referenceKey);
        return String.format(STYLED_REFERENCE_NUMBER_FORMAT, referenceNumberStr);
    }

    private Set<String> filterKeysAdded(String... referenceKeys) {
        return Arrays.stream(referenceKeys).filter(keysAdded::contains).collect(Collectors.toSet());
    }

    /**
     * Returns the reference numbers for the given keys as a string of CSV super-script styled XML to be used in a text
     * element. The text element must have the attribute {@code markup="styled"}. If none of the reference keys are in
     * the list of reference keys added, for example due to the provided text being empty, then an empty string is
     * returned.
     * @param referenceKeys
     * @return
     */
    public String getStyledReferenceNumberTextForKeys(String... referenceKeys) {

        Set<String> addedKeys = filterKeysAdded(referenceKeys);
        if (addedKeys.isEmpty()) {
            return "";
        }

        List<String> sortedReferenceNumbers = addedKeys.stream().map(this::getReferenceNumberForKey)
                .map(Integer::parseInt).sorted().map(String::valueOf)
                .collect(Collectors.toList());

        String sortedReferenceNumbersString = String.join(", ", sortedReferenceNumbers);
        return String.format(STYLED_REFERENCE_NUMBER_FORMAT, sortedReferenceNumbersString);
    }

    /**
     * Whether there are any references. Can be used to determine whether to display a references detail section.
     * @return
     */
    public boolean hasReferences() {
        return !references.isEmpty();
    }

    /**
     * Returns a {@code JRDataSource} for use as a datasource expression. The datasource contains all the reference
     * entries that have been collected for use in creating a list of references at the end of the report or report
     * section.
     * @return
     */
    public JRRewindableDataSource getDataSource() {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Collection<Map<String, ?>> resultCollection = (Collection) references;
        return new JRMapCollectionDataSource(resultCollection);
    }

    /**
     * Adds the reference text for the given reference key. This should be called before any attempt is made to get the
     * reference number or text for the given reference key. If the given reference key already exists, the reference
     * text will be appended to the existing reference text for the key.
     * @param referenceText
     * @param referenceKey
     * @return The reference key.
     */
    public String addReferenceTextForKey(String referenceText, String referenceKey) {
        return addReferenceTextForKey(referenceText, referenceKey, false);
    }

    /**
     * Adds the reference text for the given reference key. This should be called before any attempt is made to get the
     * reference number or text for the given reference key. If the given reference key already exists, the reference
     * text will either be appended or prepended to the existing reference text for the key, depending on the value of
     * {@code prependAdditional}.
     * @param referenceText
     * @param referenceKey
     * @param prependAdditional
     * @return
     */
    public String addReferenceTextForKey(String referenceText, String referenceKey, boolean prependAdditional) {

        if (keysAdded.contains(referenceKey)) {
            // Add the reference text as a new line to the existing reference text.
            for (Map<String, String> refEntry : references) {
                if (refEntry.get(REFERENCE_KEY).equals(referenceKey)) {
                    String currentText = refEntry.get(REFERENCE_TEXT);
                    // If Detail Split Type == "Prevent" then we get double calls, hence the need to check that the
                    // currentText doesn't already contain the reference Text.
                    if (!currentText.contains(referenceText)) {
                        refEntry.put(REFERENCE_TEXT, String.join("\n", prependAdditional ? referenceText : currentText,
                                prependAdditional ? currentText : referenceText));
                    }
                    return referenceKey;
                }
            }
            throw new RuntimeException("Reference key appears in keysAdded but no refEntry was found!");
        }

        if (referenceText != null && !referenceText.isEmpty()) {
            references.add(createReferenceEntry(referenceText, referenceKey));
            keysAdded.add(referenceKey);
        }

        return referenceKey;
    }

    /**
     * If the value text exceeds the given length, prepends the value text to the reference text for the given reference
     * key and returns the value text having been shortened and ellipsised. Otherwise, returns the value text unaltered
     * without adding a reference.<br><br>
     *
     * For use with data that is too long to display in a text field that should instead be referenced in a footnote.
     * Expected to be used with the same reference key as any normal reference for the given text field.
     * @param valueText
     * @param referenceKey
     * @param length
     * @return The value text if it does not exceed {@code length}, or the value text curtailed with ellipsis if it
     * does.
     */
    public String addValueTextForKeyIfLengthExceeds(String valueText, String referenceKey, int length) {

        if (valueText != null && valueText.length() > length) {
            addReferenceTextForKey(valueText, referenceKey, true);
            return valueText.substring(0, length) + "…";
        }

        return valueText;
    }

    private Map<String, String> createReferenceEntry(String referenceText, String referenceKey) {
        Map<String, String> refEntry = new HashMap<>();
        refEntry.put(REFERENCE_KEY, referenceKey);
        refEntry.put(REFERENCE_TEXT, referenceText);
        refEntry.put(REFERENCE_NUMBER, String.valueOf(currentReferenceNumber++));
        return refEntry;
    }
}
