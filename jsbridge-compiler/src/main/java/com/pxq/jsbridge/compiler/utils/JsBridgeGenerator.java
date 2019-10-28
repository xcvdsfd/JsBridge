package com.pxq.jsbridge.compiler.utils;

import com.pxq.jsbridge.annotation.Bridge;
import com.pxq.jsbridge.annotation.JsAction;
import com.pxq.jsbridge.annotation.JsError;
import com.pxq.jsbridge.annotation.UnHandle;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;


/**
 * js交互类生成工具
 * author : pxq
 * date : 19-10-24 下午11:03
 */
public class JsBridgeGenerator {
    //被Bridge注解的类
    private Element mBridgeElement;
    //Js Request出错时，默认调用的方法,即@JsError处理
    private ExecutableElement mJsErrorElement;
    //Js Request未处理时，默认调用的方法,即@UnHandle处理
    private Element mUnHandleElement;

    private void init() {
        mJsErrorElement = null;
        mUnHandleElement = null;
    }

    public void setBridgeElement(Element bridgeElement) {
        mBridgeElement = bridgeElement;
    }


    public void generate(Filer filer) throws IOException {
        init();
        Bridge bridge = mBridgeElement.getAnnotation(Bridge.class);
        String jsName = bridge.name();
        String jsMethodName = bridge.jsMethod();
        String className = mBridgeElement.getSimpleName().toString() + Consts.JS_BRIDGE_SUFFIX;
        String packageName = mBridgeElement.getEnclosingElement().toString();

        //持有一个处理对象
        FieldSpec mHandlerField = FieldSpec.builder(TypeName.get(mBridgeElement.asType()), Consts.JS_FIELD_HANDLER_NAME, Modifier.PUBLIC).build();

        //生成构造方法给handler赋值
        MethodSpec constructMethod = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.get(mBridgeElement.asType()), Consts.JS_FIELD_HANDLER_NAME)
                .addStatement("this.$N = " + Consts.JS_FIELD_HANDLER_NAME, mHandlerField)
                .build();

        //生成request处理方法
        MethodSpec mHandleMethod = generateHandleMethod(className, mHandlerField);

        //生成获取js名的方法
        MethodSpec jsNameMethod = MethodSpec.methodBuilder(Consts.JS_BRIDGE_METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", jsName)
                .build();

        //生成js交互方法
        MethodSpec jsMethod = generateJsMethod(jsMethodName, mHandlerField, mHandleMethod);

        //生成js交互类
        TypeSpec jsClass = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(Consts.JS_BRIDGE_NAME_INTERFACE_PACKAGE, Consts.JS_BRIDGE_NAME_INTERFACE_CLASSNAME))  //IJsBridge接口
                .addField(mHandlerField)
                .addMethod(constructMethod)
                .addMethod(jsMethod)
                .addMethod(jsNameMethod)
                .addMethod(mHandleMethod)
                .build();

        //生成类文件
        JavaFile.builder(packageName, jsClass)
                .build()
                .writeTo(filer);
    }

    /**
     * 生成处理request的方法
     *
     * @param mHandlerField
     * @return
     */
    private MethodSpec generateHandleMethod(String className, FieldSpec mHandlerField) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(Consts.JS_HANDLE_METHOD_NAME)
                .addParameter(TypeName.get(String.class), Consts.JS_BRIDGE_METHOD_PARAM)
                //生成 String action = JsonParse.getAction(request)
                .addStatement("String $N = $T.$N($N)",
                        Consts.VAR_ACTION_NAME,
                        ClassName.get(Consts.PARSER_PACKAGE, Consts.PARSER_CLASS_NAME),
                        Consts.PARSER_METHOD_GET_ACTION,
                        Consts.JS_BRIDGE_METHOD_PARAM);
                //添加一个Log打印 Log.e(TAG, request)
//                .addStatement("$T.e($S, $N)",
//                        ClassName.get("android.util", "Log"),
//                        className,
//                        Consts.VAR_ACTION_NAME);
        //填充方法
        TypeElement typeElement = (TypeElement) mBridgeElement;
        //开始switch
        if (!CollectionUtils.isEmpty(typeElement.getEnclosedElements())) {
            builder.beginControlFlow("switch($N)", Consts.VAR_ACTION_NAME);
        }
        for (Element element : typeElement.getEnclosedElements()) {
            JsAction jsAction = element.getAnnotation(JsAction.class);
            UnHandle unHandle = element.getAnnotation(UnHandle.class);
            JsError jsError = element.getAnnotation(JsError.class);
            if (jsAction == null && unHandle == null && jsError == null) {
                continue;
            }
            if (element.getKind() != ElementKind.METHOD) {
                Logger.error("JsAction UnHandle JsError注解必须作用在方法上");
                return null;
            }
            //多个注解的情况，优先JsAction 生成switch的case分支
            ExecutableElement executableElement = (ExecutableElement) element;
            if (jsAction != null) {
                Logger.info(element.getSimpleName().toString() + " " + jsAction.value() + " ");
                //只允许一个参数
                if (executableElement.getParameters().size() > 1) {
                    Logger.error("@JsAction 作用的方法只允许一个参数");
                } else if (executableElement.getParameters().size() == 1) {
                    VariableElement variableElement = executableElement.getParameters().get(0);
                    //生成case "action" : JsonParse.parse(request, Class);
                    builder.addStatement("case $S: \n$N.$N($T.$N($N, $T.class))",
                            jsAction.value(),
                            mHandlerField,
                            executableElement.getSimpleName(),
                            ClassName.get(Consts.PARSER_PACKAGE, Consts.PARSER_CLASS_NAME),
                            Consts.PARSER_METHOD_PARSE,
                            Consts.JS_BRIDGE_METHOD_PARAM,
                            ClassName.get(variableElement.asType()));
                } else {
                    builder.addStatement("case $S: \n$N.$N()", jsAction.value(), mHandlerField, executableElement.getSimpleName());

                }

                builder.addStatement("break");
                continue;
            }

            if (unHandle != null) {
                if (mUnHandleElement != null){
                    Logger.warning("@UnHandle作用的方法只能有一个有效");
                }
                //校验,参数只允许有一个且为String类型
                //只允许一个参数
                if (executableElement.getParameters().size() != 1) {
                    Logger.error("@UnHandle 作用的方法只允许一个参数: " + executableElement.getSimpleName());
                }
                VariableElement variableElement = executableElement.getParameters().get(0);
                //String类型判断
                if (!TypeChecker.isString(variableElement.asType())) {
                    Logger.error("@UnHandle 作用的方法只允许一个参数且为String 类型 " + variableElement.asType().toString());
                }
                mUnHandleElement = element;
                continue;
            }
            //JsError校验
            //不能超过2个参数
            if (mJsErrorElement != null){
                if (mUnHandleElement != null){
                    Logger.warning("@JsError作用的方法只能有一个有效");
                }
            }
            VariableElement variableElement;
            switch (executableElement.getParameters().size()) {
                case 0: //没有参数

                    break;
                case 1: //只有一个参数，判断类型
                    variableElement = executableElement.getParameters().get(0);
                    //String类型判断
                    if (!TypeChecker.isString(variableElement.asType()) &&
                            !TypeChecker.isException(variableElement.asType())) {
                        Logger.error("@JsError 作用的方法参数个数为1时必须为String或Exception类型 " + variableElement.asType().toString());
                    }

                    break;
                case 2: //2个参数，判断类型和参数顺序String Exception
                    if (!TypeChecker.isString(executableElement.getParameters().get(0).asType()) && !TypeChecker.isException(executableElement.getParameters().get(1).asType())) {
                        Logger.error("@JsError 作用的方法参数个数为2时必须为String和Exception类型 " +
                                executableElement.getParameters().get(0).asType().toString() + " " +
                                executableElement.getParameters().get(1).asType().toString());
                    }
                    break;
                default:
                    Logger.error("@JsError 作用的方法参数个数不能超过2 : " + executableElement.getSimpleName().toString() + " " + executableElement.getParameters().size());
            }

            mJsErrorElement = executableElement;


        }
        //处理@UnHandle注解 生成switch 的default分支
        if (mUnHandleElement != null) {
            builder.addStatement("default : $N.$N(" + Consts.JS_BRIDGE_METHOD_PARAM + ")", mHandlerField, mUnHandleElement.getSimpleName());
        }
        //结束switch
        if (!CollectionUtils.isEmpty(typeElement.getEnclosedElements())) {
            builder.endControlFlow();
        }
        return builder
                .addException(TypeName.get(Exception.class))
                .build();
    }

    /**
     * 生成注解了@JavascriptInterface的方法
     *
     * @param jsMethodName
     * @param mHandlerField
     * @return
     */
    private MethodSpec generateJsMethod(String jsMethodName, FieldSpec mHandlerField, MethodSpec handleMethod) {

        MethodSpec.Builder builder = MethodSpec.methodBuilder(jsMethodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get(Consts.ANNOTATION_JSINTERFACE_PACKAGE, Consts.ANNOTATION_JSINTERFACE_NAME)) //添加JavascriptInterface注解
                .addParameter(String.class, Consts.JS_BRIDGE_METHOD_PARAM)  //方法参数
                .beginControlFlow("try")
                .addStatement("$N(" + Consts.JS_BRIDGE_METHOD_PARAM + ")", handleMethod)
                .nextControlFlow("catch ($T e)", Exception.class);
        if (mJsErrorElement != null) {
            switch (mJsErrorElement.getParameters().size()) {
                case 0:
                    builder.addStatement("$N.$N()", mHandlerField, mJsErrorElement.getSimpleName());
                    break;
                case 1:
                    if (TypeChecker.isString(mJsErrorElement.getParameters().get(0).asType())) {
                        builder.addStatement("$N.$N($N)", mHandlerField, mJsErrorElement, Consts.JS_BRIDGE_METHOD_PARAM);
                    }
                    if (TypeChecker.isException(mJsErrorElement.getParameters().get(0).asType())){
                        builder.addStatement("$N.$N(e)", mHandlerField, mJsErrorElement.getSimpleName());
                    }
                    break;
                case 2:
                    builder.addStatement("$N.$N($N, e)", mHandlerField, mJsErrorElement.getSimpleName(), Consts.JS_BRIDGE_METHOD_PARAM);
                    break;
            }
        }
        builder.endControlFlow();


        return builder
                .returns(void.class)
                .build();
    }


}