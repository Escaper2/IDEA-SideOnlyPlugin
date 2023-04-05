package escaper2.testtask.sideonlyplugin;

import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import escaper2.testtask.sideonlyplugin.annotation.Side;
import org.jetbrains.annotations.NotNull;


public class SideOnlyInspectionTool extends AbstractBaseJavaLocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                System.out.println(expression);
                checkSideOnly(expression, holder);
            }

            @Override
            public void visitMethod(PsiMethod method) {
                super.visitMethod(method);
                checkSideOnly(method, holder);
            }

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                checkSideOnly(expression.getMethodExpression(), holder);
            }

            @Override
            public void visitNewExpression(PsiNewExpression expression) {
                super.visitNewExpression(expression);
                PsiMethod constructor = expression.resolveConstructor();
                if (constructor != null) {
                    checkSideForConstructor(expression.getClassReference(), constructor, holder);

                } else {
                    checkSideOnly(expression.getClassReference(), holder);
                }
            }

            private void checkSideOnly(PsiElement element, ProblemsHolder holder) {
                if (element == null) return;
                PsiElement resolved;
                if (element instanceof PsiReference) {
                    resolved = ((PsiReference) element).resolve();
                } else {
                    resolved = element;
                }
                if (resolved == null) return;
                Side elementSide = getSide(resolved);
                PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                if (containingMethod != null) {
                    Side methodSide = getSide(containingMethod);
                    elementSide = methodSide.compareSides(elementSide);
                }
                if (elementSide == Side.INVALID) {
                    holder.registerProblem(element, "Can not access side-only " + element + " from here");
                }
            }

            private void checkSideForConstructor(PsiElement element, PsiMethod constructor, ProblemsHolder holder) {
                if (element == null) return;
                PsiElement resolved;
                if (element instanceof PsiReference) {
                    resolved = ((PsiReference) element).resolve();
                } else {
                    resolved = element;
                }
                if (resolved == null) return;
                Side elementSide = getSide(resolved);
                Side constructorSide = getSide(constructor);
                PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                if (containingMethod != null) {
                    Side methodSide = getSide(containingMethod);
                    elementSide = methodSide.compareSides(constructorSide);
                }
                if (elementSide == Side.INVALID) {
                    holder.registerProblem(element, "Can not access side-only  " + element + " from here");
                }

            }

            private Side getSide(PsiElement element) {
                Side side = Side.BOTH;
                if (element instanceof PsiModifierListOwner) {
                    PsiModifierList modifierList = ((PsiModifierListOwner) element).getModifierList();
                    if (modifierList != null) {
                        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                            if (annotation.getQualifiedName().endsWith(".SideOnly")) {
                                PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
                                if (value != null && value.getText().endsWith(".CLIENT")) {
                                    side = Side.CLIENT;
                                } else if (value != null && value.getText().endsWith(".SERVER")) {
                                    side = Side.SERVER;
                                }
                            }
                        }
                    }
                }
                if (element instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) element;
                    PsiClass superClass = psiClass.getSuperClass();
                    side = side.compareSides(getSide(psiClass.getContainingClass()));

                    for (PsiClass intf : psiClass.getInterfaces()) {
                        side = side.compareSides(getSide(intf));
                        if (side == Side.INVALID) return Side.INVALID;
                    }

                    if (side == Side.INVALID) return Side.INVALID;

                    if (superClass == null || superClass.getQualifiedName().equals("java.lang.Object")) return side;

                    side = side.compareSides(getSide(psiClass.getSuperClass()));


                } else if (element instanceof PsiMethod) {
                    return side.compareSides(getSide(((PsiMethod) element).getContainingClass()));

                } else if (element instanceof PsiField) {
                    return side.compareSides(getSide(((PsiField) element).getContainingClass()));
                }

                return side;
            }
        };
    }
}

