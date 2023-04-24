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
    @NotNull
    @Override
    public SettingsKey<NoSettings> getKey() {
        return new SettingsKey<>("TestInlayHintsProvider");
    }

    @Nullable
    @Override
    public NoSettings createSettings() {
        @Nullable NoSettings NoSettings = new NoSettings();
        return NoSettings;
    }

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull NoSettings settings) {
        return null;
    }

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

    @Override
    public boolean isLanguageSupported(@NotNull Language language) {
        return language.is(JavaLanguage.INSTANCE);
    }

    @Override
    public boolean isVisibleInSettings() {
        return false;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
        return null;
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return null;
    }
}