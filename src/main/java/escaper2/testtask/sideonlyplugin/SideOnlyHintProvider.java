/**
 * Provides inlay hints for the SideOnly inspection tool in IntelliJ IDEA.
 * This class implements the InlayHintsProvider interface and overrides its methods.
 * It also contains helper methods to get the depth of an element, check if it has a SideOnly annotation,
 * and get the side for the hint.
 */

package escaper2.testtask.sideonlyplugin;

import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.BlockInlayPriority;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


public class SideOnlyHintProvider implements InlayHintsProvider<NoSettings> {

    /**
     * Returns the settings key for this provider.
     *
     * @return the settings key for this provider
     */

    @NotNull
    @Override
    public SettingsKey<NoSettings> getKey() {
        return new SettingsKey<>("TestInlayHintsProvider");
    }


    /**
     * Creates and returns the settings for this provider.
     *
     * @return the settings for this provider
     */

    @Nullable
    @Override
    public NoSettings createSettings() {
        @Nullable NoSettings NoSettings = new NoSettings();
        return NoSettings;
    }

    /**
     * Creates and returns the configurable for this provider.
     *
     * @param settings the settings for this provider
     * @return the configurable for this provider
     */

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull NoSettings settings) {
        return null;
    }

    /**
     * Returns the collector for this provider and creates inlay hints for the specified PsiElement
     * based on the SideOnly inspection tool.
     *
     * @param file     the PsiFile to collect hints for
     * @param editor   the Editor to display hints in
     * @param settings the settings for this provider
     * @param __       the InlayHintsSink to add hints to
     * @return the collector for this provider
     */

    @Nullable
    @Override
    public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                               @NotNull Editor editor,
                                               @NotNull NoSettings settings,
                                               @NotNull InlayHintsSink __) {
        return new FactoryInlayHintsCollector(editor) {
            @Override
            public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
                if (!(element instanceof PsiMethod || element instanceof PsiClass )) return true;
                if (((PsiTypeParameterListOwner) element).getContainingClass() instanceof PsiAnonymousClass) return true;

                SideOnlyInspectionTool inspector = new SideOnlyInspectionTool();
                if (hasAnnotation(element, inspector)) return true;

                Set<String> sideForHint = getSideForHint(element, inspector);

                if (!(sideForHint.size() == 2)) {
                    int offsetCounter = getDepth(element);
                    int spacesCount = EditorUtil.getPlainSpaceWidth(editor) * offsetCounter;
                    String spaces = new String(new char[spacesCount]).replace('\0', ' ');
                    int offset = element.getTextRange().getStartOffset();

                    InlayPresentation hint = getFactory().text(spaces + "@SideOnly(" + sideForHint + ")");
                    sink.addBlockElement(offset, false, true,  BlockInlayPriority.ANNOTATIONS, hint);
                }
                return true;
            }
        };
    }

    /**
     * Returns the depth of the given element in the PSI tree.
     *
     * @param element the PsiElement to get the depth of
     * @return the depth of the given element
     */

    private int getDepth(PsiElement element) {
        int depth = 0;

        PsiElement parent = element.getParent();
        while (parent != null) {
            depth++;
            parent = parent.getParent();
            if (!(parent instanceof PsiMethod || parent instanceof PsiClass )) break;
        }

        if (element instanceof PsiAnonymousClass) return depth + getDepth(Objects.requireNonNull(PsiTreeUtil.getParentOfType(element, PsiMethod.class)));
        return depth;
    }

    /**
     * Returns true if the given element has a SideOnly annotation, false otherwise.
     *
     * @param element   the PsiElement to check for a SideOnly annotation
     * @param inspector the SideOnlyInspectionTool to use for resolving the element
     * @return true if the given element has a SideOnly annotation, false otherwise
     */

    private boolean hasAnnotation(PsiElement element, SideOnlyInspectionTool inspector) {
        if (element == null) return false;

        var resolved = inspector.getResolved(element);
        if (resolved == null) return false;

        var owner  = ((PsiModifierListOwner) element);

        PsiAnnotation[] annotations = owner.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
            if (value != null) return true;
        }
        return false;
    }

    /**
     * Returns the side for the @SideOnly annotation for given PsiElement, based on its
     * containing class and method.
     *
     * @param element the {@link PsiElement} for which to get the side(s)
     * @param inspector the {@link SideOnlyInspectionTool} instance to use for inspection
     * @return a {@link Set} of strings representing the side(s) for the {@code @SideOnly} annotation on the given element,
     */

    private Set<String> getSideForHint(PsiElement element, SideOnlyInspectionTool inspector) {
        Set<String> emptySide = new HashSet<>();

        var resolved = inspector.getResolved(element);
        if (resolved == null) return emptySide;

        Set<String> elementSide = inspector.getSide((PsiModifierListOwner) resolved);
        PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

        if (containingMethod != null) {
            Set<String> methodSide = inspector.getSide(containingMethod);

            if (containingMethod.getContainingClass() instanceof PsiAnonymousClass && methodSide.isEmpty()) {
                PsiNewExpression newExpr = PsiTreeUtil.getParentOfType(inspector.getResolved(containingMethod), PsiNewExpression.class);
                assert newExpr != null;
                PsiJavaCodeReferenceElement ref = newExpr.getClassOrAnonymousClassReference();
                return emptySide;
            }

            elementSide.retainAll(methodSide);
            if (methodSide.size() > 1 && elementSide.size() == 1) return  emptySide;
        }
        if (elementSide.isEmpty()) return emptySide;

        return elementSide;
    }

    /**
     * Determines if a particular language is supported by the plugin.
     *
     * @param language the language to check for support
     * @return true if the language is supported, false otherwise
     */

    @Override
    public boolean isLanguageSupported(@NotNull Language language) {
        return language.is(JavaLanguage.INSTANCE);
    }

    /**
     * Indicates whether the inspection tool should be visible in the settings menu.
     *
     * @return false, indicating that the tool should not be visible in settings
     */

    @Override
    public boolean isVisibleInSettings() {
        return false;
    }

    /**
     * Returns the name of the inspection tool.
     *
     * @return the name of the inspection tool as a string
     */

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
        return null;
    }

    /**
     * Returns the preview text for the inspection tool.
     *
     * @return the preview text as a string or null if there is no preview text
     */

    @Nullable
    @Override
    public String getPreviewText() {
        return null;
    }
}