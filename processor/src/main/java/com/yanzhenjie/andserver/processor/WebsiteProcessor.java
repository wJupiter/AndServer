/*
 * Copyright 2018 Yan Zhenjie.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yanzhenjie.andserver.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.yanzhenjie.andserver.annotation.Website;
import com.yanzhenjie.andserver.processor.util.Constants;
import com.yanzhenjie.andserver.processor.util.Logger;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Created by YanZhenjie on 2018/9/17.
 */
@AutoService(Processor.class)
public class WebsiteProcessor extends BaseProcessor {

    private Filer mFiler;
    private Elements mElements;
    private Types mTypes;
    private Logger mLog;

    private TypeName mOnRegisterType;
    private TypeName mRegisterType;

    private TypeMirror mWebstieMirror;
    private TypeName mWebsite;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        mFiler = processingEnv.getFiler();
        mElements = processingEnv.getElementUtils();
        mTypes = processingEnv.getTypeUtils();
        mLog = new Logger(processingEnv.getMessager());

        mOnRegisterType = TypeName.get(mElements.getTypeElement(Constants.ON_REGISTER_TYPE).asType());
        mRegisterType = TypeName.get(mElements.getTypeElement(Constants.REGISTER_TYPE).asType());

        mWebstieMirror = mElements.getTypeElement(Constants.WEBSITE_TYPE).asType();
        mWebsite = TypeName.get(mWebstieMirror);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        if (CollectionUtils.isEmpty(set)) return false;

        List<TypeElement> elements = findAnnotation(roundEnv);
        if (!elements.isEmpty()) createRegister(elements);
        return true;
    }

    private List<TypeElement> findAnnotation(RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(Website.class);
        List<TypeElement> elements = new ArrayList<>();

        for (Element element : set) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement)element;

                Set<Modifier> modifiers = typeElement.getModifiers();
                Validate.isTrue(modifiers.contains(Modifier.PUBLIC), "The modifier public is missing on %s.",
                    typeElement.getQualifiedName());

                TypeMirror superclass = typeElement.getSuperclass();
                if (superclass == null) {
                    mLog.w(String.format("The annotation Website must be used in a subclass of [Website] on %s.",
                        typeElement.getQualifiedName()));
                    continue;
                }

                if (mTypes.isSubtype(superclass, mWebstieMirror)) {
                    elements.add(typeElement);
                    break;
                } else {
                    mLog.w(String.format("The annotation Website must be used in a subclass of [Website] on %s.",
                        typeElement.getQualifiedName()));
                }
            }
        }
        return elements;
    }

    private void createRegister(List<TypeElement> elements) {
        TypeName typeName = ParameterizedTypeName.get(ClassName.get(List.class), mWebsite);
        FieldSpec hostField = FieldSpec.builder(typeName, "mList", Modifier.PRIVATE).build();

        CodeBlock.Builder rootCode = CodeBlock.builder();
        for (TypeElement type : elements) {
            mLog.i(String.format("------ Processing %s ------", type.getSimpleName()));
            rootCode.addStatement("this.mList.add(new $T())", type);
        }
        MethodSpec rootMethod = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addStatement("this.mList = new $T<>()", ArrayList.class)
            .addCode(rootCode.build())
            .build();

        MethodSpec registerMethod = MethodSpec.methodBuilder("onRegister")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(mRegisterType, "register")
            .beginControlFlow("for ($T item : mList)", mWebsite)
            .addStatement("register.addAdapter(item)")
            .endControlFlow()
            .build();

        String packageName = Constants.REGISTER_PACKAGE;
        TypeSpec adapterClass = TypeSpec.classBuilder("WebsiteRegister")
            .addJavadoc(Constants.DOC_EDIT_WARN)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(mOnRegisterType)
            .addField(hostField)
            .addMethod(rootMethod)
            .addMethod(registerMethod)
            .build();

        JavaFile javaFile = JavaFile.builder(packageName, adapterClass).build();
        try {
            javaFile.writeTo(mFiler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void addAnnotation(Set<Class<? extends Annotation>> classSet) {
        classSet.add(Website.class);
    }
}