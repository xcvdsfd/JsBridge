# JsBridge
web和原生app交互简单封装
# 使用方法
## 交互数据约束
```
{
    "action" : “XXXX”,
    "data" : {
	    "xxx" : "xxxx"
        ...
     }
}
```
## 添加依赖

```
implementation project(':jsbridge-core')
annotationProcessor project(':jsbridge-compiler')
```

## 编写实现类
```
@Bridge(name = "android") //window.andriod
@JsConfig(jsMethod = "call", actionName = "function", paramsName = "data") //window.android.call()
public class BridgeTest {

    private static final String TAG = "BridgeTest";

    @JsAction("test")
    public void test(){
        Log.i(TAG, "test: ");
    }


    @JsAction("testData")
    public void testData(TestBean test){
        Log.i(TAG, "testData: " + test.name +" " +  test.data);
    }

    @UnHandle
    public void UnHandle(String request){
        Log.w(TAG, "UnHandle: " + request);
    }

    @JsError
    public void error(String request, Exception e){
        Log.e(TAG, "error: " +request, e);
    }
    
    @JsFunc
    public String func(String request){
        return "func data";
    }
    
}

```

## 绑定
```
JsBridge.bind(webView, new BridgeTest());
```

## web调用方式
```javascript
window.android.call(json);
//同步方式
window.android.callSync(json);
```

