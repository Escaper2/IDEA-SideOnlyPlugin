/**
 This class represents an inspection tool for IntelliJ IDEA that checks if a code element is marked with the
 SideOnly annotation and if it is being accessed from the wrong side. The SideOnly annotation is used to mark code
 elements that should only be accessed from one side of a client-server application, such as the client or server side.
 If an element marked with the SideOnly annotation is accessed from the wrong side, this inspection tool will report a
 problem.
 */


package escaper2.testtask.sideonlyplugin;

import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import escaper2.testtask.sideonlyplugin.annotation.Side;
import org.jetbrains.annotations.NotNull;


public class SideOnlyInspectionTool extends AbstractBaseJavaLocalInspectionTool {

    /**
     * Builds a visitor for the code elements that this inspection tool checks.
     *
     * @param holder      The holder that collects problems found during the inspection.
     * @param isOnTheFly  True if the inspection is done on-the-fly, false if it is done on demand.
     * @return            The visitor.
     */

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {

            /**
             * Checks if the referenced element is marked with the SideOnly annotation and if it is being accessed
             * from the wrong side.
             *
             * @param expression  The reference expression to visit.
             */

            @Override
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                checkSideOnly(expression, holder);
            }

            /**
             * Checks if the method is marked with the SideOnly annotation and if it is being accessed from the wrong side.
             *
             * @param method  The method to visit.
             */

            @Override
            public void visitMethod(PsiMethod method) {
                super.visitMethod(method);
                checkSideOnly(method, holder);
            }

            /**
             * Checks if the method being called is marked with the SideOnly annotation and if it is being accessed
             * from the wrong side.
             *
             * @param expression  The method call expression to visit.
             */

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                checkSideOnly(expression.getMethodExpression(), holder);
            }

            /**
             * Checks if the class being instantiated is marked with the SideOnly annotation and if it is being accessed
             * from the wrong side. Also checks if the constructor being called is marked with the SideOnly annotation
             * and if it is being accessed from the wrong side.
             *
             * @param expression  The new expression to visit.
             */

            @Override
            public void visitNewExpression(PsiNewExpression expression) {
                super.visitNewExpression(expression);
                PsiMethod constructor = expression.resolveConstructor();

                if (constructor != null) checkSideForConstructor(expression.getClassReference(), constructor, holder);
                else checkSideOnly(expression.getClassReference(), holder);
            }

            /**
             * Resolves an element to its actual reference.
             *
             * @param element  The element to resolve.
             * @return         The resolved element, or null if the element cannot be resolved.
             */

            private PsiElement getResolved(PsiElement element) {
                if (element == null) return null;
                PsiElement resolved;

                if (element instanceof PsiReference) resolved = ((PsiReference) element).resolve();
                else resolved = element;

                return resolved;
            }

            /**
             * Checks the given PsiElement for the "@SideOnly" annotation and compares its value to the context side.
             * If the element's side is invalid for the current context, then a problem is registered with
             * the ProblemsHolder.
             *
             * @param element the PsiElement to check for the "@SideOnly" annotation
             * @param holder the ProblemsHolder to use for registering problems
             */

            private void checkSideOnly(PsiElement element, ProblemsHolder holder) {
                var resolved = getResolved(element);
                if (resolved == null) return;

                Side elementSide = getSide(resolved);
                PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

                if (containingMethod != null) {
                    Side methodSide = getSide(containingMethod);
                    elementSide = methodSide.compareSides(elementSide);
                }

                if (elementSide == Side.INVALID) holder
                        .registerProblem(element, "Can not access side-only " + element + " from here");

            }

            /**
             * Checks the given PsiElement for the "@SideOnly" annotation and compares its value against the side of the containing
             * constructor. If the element's side is invalid for the current context, then a problem is registered with
             the ProblemsHolder.
             *
             * @param element the PsiElement to check for the "@SideOnly" annotation
             * @param constructor the PsiMethod representing the constructor to compare against
             * @param holder the ProblemsHolder to use for registering problems
             */

            private void checkSideForConstructor(PsiElement element, PsiMethod constructor, ProblemsHolder holder) {
                var resolved = getResolved(element);
                if (resolved == null) return;

                Side elementSide = getSide(resolved);
                Side constructorSide = getSide(constructor);
                PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

                if (containingMethod != null) {
                    Side methodSide = getSide(containingMethod);
                    elementSide = methodSide.compareSides(constructorSide);
                }

                if (elementSide == Side.INVALID) holder
                        .registerProblem(element, "Can not access side-only  " + element + " from here");


            }

            /**
             * Determines the side (client or server) of a given PsiElement based on the presence of the @SideOnly
             * annotation and the inheritance hierarchy of the element's class.
             *
             * @param element the PsiElement to check the side for
             * @return the determined Side of the element (BOTH, CLIENT, SERVER, or INVALID)
             */

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


                }
                else if (element instanceof PsiMethod) return side.compareSides(getSide(((PsiMethod) element).getContainingClass()));

                else if (element instanceof PsiField) return side.compareSides(getSide(((PsiField) element).getContainingClass()));

                return side;
            }
        };
    }
}

