/**
 * This class represents an inspection tool for IntelliJ IDEA that checks if a code element is marked with the
 * SideOnly annotation and if it is being accessed from the wrong side. The SideOnly annotation is used to mark code
 * elements that should only be accessed from one side of a client-server application, such as the client or server side.
 * If an element marked with the SideOnly annotation is accessed from the wrong side, this inspection tool will report a
 * problem.
 */


package escaper2.testtask.sideonlyplugin;

import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;


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

                Set<String> elementSide = getSide((PsiModifierListOwner) resolved);
                PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

                if (containingMethod != null) {
                    Set<String> methodSide = getSide(containingMethod);
                    elementSide.retainAll(methodSide);
                    if (methodSide.size() > 1 && elementSide.size() == 1) registerProblem(holder, element);
                }
                if (elementSide.isEmpty()) registerProblem(holder, element);
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

                Set<String> elementSide = getSide((PsiModifierListOwner) resolved);
                Set<String> constructorSide = getSide(constructor);
                PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

                if (containingMethod != null) {
                    Set<String> methodSide = getSide(containingMethod);
                    elementSide.retainAll(methodSide);
                    elementSide.retainAll(constructorSide);
                    if (methodSide.size() > 1 && elementSide.size() == 1) registerProblem(holder, element);
                }
                else elementSide.retainAll(constructorSide);

                if (elementSide.isEmpty()) registerProblem(holder, element);
            }

            /**
             * Registers a problem with the ProblemsHolder.
             *
             * @param holder The ProblemsHolder to register the problem with.
             * @param element The element that caused the problem.
             */

            private void registerProblem(ProblemsHolder holder, PsiElement element) {
                holder.registerProblem(element, "Can't access side-only " + element.getText() + " from here");
            }

            /**
             * Gets the side(s) that a PsiModifierListOwner is marked with.
             *
             * @param owner The PsiModifierListOwner to get the side(s) of.
             * @return A set of side(s) that the owner is marked with.
             */

            private Set<String> getSide(PsiModifierListOwner owner) {
                Set<String> side = new HashSet<>(Arrays.asList("CLIENT", "SERVER"));
                if (owner == null) return compareSides(owner, side);
                PsiAnnotation[] annotations = owner.getAnnotations();
                for (PsiAnnotation annotation : annotations) {
                    PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
                    if (value != null) {
                        String[] name = value.getText().replaceAll("[{}]|Side\\.", "").split(", ");
                        side.retainAll(Arrays.asList(name));
                    }
                }
                return compareSides(owner, side);
            }

            /**
             * Compares the side(s) of a code element to the side(s) of its containing class, interfaces, and/or superclass.
             *
             * @param element The code element to compare sides for.
             * @param side The set of sides to compare to.
             * @return A set of strings representing the side(s) that the code element is marked with.
             */

            private Set<String> compareSides(PsiElement element, Set<String> side) {
                if (element instanceof PsiAnonymousClass) {
                    PsiClass psiClass = (PsiClass) element;
                    for (PsiClass intf : psiClass.getInterfaces()) {
                        side.retainAll(getSide(intf));
                    }

                    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                    side.retainAll(getSide(containingMethod));

                    if (side.isEmpty()) {
                        PsiNewExpression newExpr = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
                        PsiJavaCodeReferenceElement ref = newExpr.getClassOrAnonymousClassReference();
                        registerProblem(holder, ref);
                    }
                    return side;
                }

                else if (element instanceof PsiClass) {
                    PsiClass psiClass = (PsiClass) element;
                    PsiClass superClass = psiClass.getSuperClass();
                    side.retainAll(getSide(psiClass.getContainingClass()));

                    for (PsiClass intf : psiClass.getInterfaces()) {
                        side.retainAll(getSide(intf));
                        if (side.isEmpty()) return side;
                    }

                    if (side.isEmpty()) return side;
                    if (superClass == null || superClass.getQualifiedName().equals("java.lang.Object")) return side;

                    side.retainAll(getSide(psiClass.getSuperClass()));

                }

                else if (element instanceof PsiMethod) {
                    side.retainAll(getSide(((PsiMethod) element).getContainingClass()));
                    return side;
                }

                else if (element instanceof PsiField) {
                    side.retainAll(getSide(((PsiField) element).getContainingClass()));
                    return side;
                }
                return side;
            }
        };
    }
}

