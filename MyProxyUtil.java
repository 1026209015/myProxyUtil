package com.proxy.proxyutil;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;

/**
 * 自己手写的动态代理类： 模拟JDK的Proxy类简单功能，代理的类只实现一个接口。
 * 思路：JDK的Proxy是动态生成class文件然后加载到JVM中，那么我们也可以采用这种思路，
 * 首先
 * 1.我们要明白生成的代理类class文件应该是怎么样的，用一个字符串来拼接java代码，
 *   最终通过这个字符对象表示的java代码用File写入到本地class文件？
 * 2.代理类文件中哪些字眼是可以动态来表示的？
 * 3.这些字眼用什么办法去替换并且可以根据传入的参数来准确替换？
 * 4.class文件有了要怎么加载到JVM中并返回
 * 总结步骤就是1.动态生成.java文件 2.编译成.class 3.加载.class到jvm 4.代理传入的目标对象，执行代理方法
 * @Author: Yang
 * @history 2020-04-26 1:15
 */
public class MyProxyUtil implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String NEW_LINE = "\n";

    private static final String TAB = "\t";

    private static final String FILE_PREFIX = "Yang$Proxy0";

    private static final String PROXY_PACKAGE = "com.yang.proxyutil";

    private static final String FILE_TYPE =  ".java";

    private static final String FILE_CLASS_TYPE = ".class";

    private static final String RETURN_FLAG = "return";

    private static final String VOID_FLAG = "void";

    /**
     * 生成代理方法
     * @param clazz 代理接口
     * @param target 目标代理对象
     * @throws IllegalArgumentException
     */
    public static Object newProxyInstance(Class clazz, Object target) throws IllegalArgumentException {

        //1.动态生成.java内容，保存java文件到本地磁盘
        String javaContent = generateJavaContent(clazz, target);
        File file = generateJavaFile2Path(javaContent);

        //2.使用jdk自带的javac编译.java文件为.class文件，注意这里要求要有jdk环境，生产环境上可能只有jre而没有jdk
        javacJavaFile(file);

        //3.使用类加载器加载.class，产生Proxy代理对象并返回
        Object o = generateProxyFromClass(clazz, target);

        //4.删除生成的.java文件，并返回代理类
        file.delete();

        return o;
    }


    /**
     * 根据传入的接口生成代理类的java文件内容
     * @param object
     * @param target
     * @return String
     */
    private static String generateJavaContent(Class object, Object target){
        //接口信息
        String intf = object.getInterfaces()[0].getSimpleName();
        String className = object.getInterfaces()[0].getName();
        Method[] methods = object.getInterfaces()[0].getDeclaredMethods();

        //目标对象信息
        String targetIntf = target.getClass().getSimpleName();
        String targetName = target.getClass().getName();

        StringBuffer javaContent = new StringBuffer();
        //1.根据传入的接口信息，参考MyProxyService手写.java文件
        javaContent.append("package " + PROXY_PACKAGE + ";" + NEW_LINE);
        javaContent.append("import " + className + ";" + NEW_LINE);
        javaContent.append("import " + targetName + ";" + NEW_LINE);

        //类名
        javaContent.append("public class " + FILE_PREFIX + " implements " + intf + "{" + NEW_LINE);

        //属性
        javaContent.append(TAB + "private " + targetIntf + " target;" + NEW_LINE);
        javaContent.append(TAB + "public Yang$Proxy0(" + targetIntf + " target){" + NEW_LINE);
        javaContent.append(TAB + TAB + "this.target = target;" + NEW_LINE);
        javaContent.append(TAB + "}" + NEW_LINE);

        //方法：遍历方法，获取方法名，入参，出餐
        for(Method method : methods) {
            String methodName = method.getName();
            Class<?>[] paramTypes = method.getParameterTypes();
            Parameter[] params = method.getParameters();
            String retType = method.getReturnType().getSimpleName();
            javaContent.append(TAB + "public " + retType + " " + methodName + "(");
            int count = 0;
            String paramPrefix = "p";
            String methodContent = "";
            String paramContent = "";
            StringBuffer methodParamBuffer = new StringBuffer();
            StringBuffer paramBuffer = new StringBuffer();
            for (Class paramType : paramTypes) {
                methodParamBuffer.append(paramType.getSimpleName() + " " + paramPrefix + count + ",");
                paramBuffer.append(paramPrefix + count + ",");
                count++;
            }
            methodContent = methodParamBuffer.toString();
            paramContent = paramBuffer.toString();
            if (null != methodContent && "" != methodContent) { //方法入参截取最后一个,
                methodContent = methodContent.substring(0, methodContent.length() - 1);
                paramContent =  paramContent.substring(0, paramContent.length() - 1);
            }
            javaContent.append(methodContent + "){" + NEW_LINE);
            javaContent.append(TAB + TAB + "System.out.println(\"" + FILE_PREFIX + " " + methodName + "...\");" + NEW_LINE);
            //返回：void或有参
            if (retType.contains(VOID_FLAG)) {
                javaContent.append(TAB + TAB + RETURN_FLAG + ";" + NEW_LINE);
            } else {
                javaContent.append(TAB + TAB + RETURN_FLAG + " target." + methodName + "(" + paramContent + ");" + NEW_LINE);
            }
            javaContent.append(TAB + "}" + NEW_LINE);
        }
        javaContent.append("}" + NEW_LINE);
        return javaContent.toString();
    }

    /**
     * 生成java文件存储到本地磁盘（代理类声明的包目录下）
     * @param javaContent
     * @return File
     */
    private static File generateJavaFile2Path(String javaContent){
        String path = MyProxyUtil.class.getResource("").getPath();
        File file = new File(path + FILE_PREFIX + FILE_TYPE);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            byte[] bytes = javaContent.getBytes();
            fos.write(bytes);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(null != fos){
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return file;
        }
    }

    /**
     * javac执行java文件
     * @param file
     * @throws IOException
     */
    private static void javacJavaFile(File file) {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        //标准的文件管理
        StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);
        //对指定文件进行编译
        Iterable javaFileObjects = standardFileManager.getJavaFileObjects(file);
        //创建编译任务
        JavaCompiler.CompilationTask task =
                javaCompiler.getTask(null,standardFileManager,null,null,null,javaFileObjects);
        //开启任务
        task.call();
        //关闭管理器
        try {
            standardFileManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 使用jdk自带的编译器编译class文件，产生代理类返回
     * @param clazz 代理接口
     * @param target 目标对象
     * @return Object
     */
    private static Object generateProxyFromClass(Class clazz, Object target){
        Class<?> proxyClazz = null;
        try {
            //注意这里加载的class文件并不是磁盘的地址，而是包地址
            String myPath = MyProxyUtil.class.getResource("").getPath();
            File file = new File(myPath+FILE_PREFIX + FILE_CLASS_TYPE);
            MyClassLoader myClassLoader = new MyClassLoader();
            proxyClazz = myClassLoader.loadClass(FILE_PREFIX+FILE_CLASS_TYPE, file);
            //使用反射获取构造器创建代理对象实例
            Constructor<?> constructor = proxyClazz.getConstructor(new Class<?>[]{clazz});
            Object o = constructor.newInstance(target);
            return o;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 自定义类加载器
     */
    static class MyClassLoader extends ClassLoader {
        public synchronized Class<?> loadClass(String name, File file) throws FileNotFoundException {
            Class<?> cls = findLoadedClass(name);
            if (cls != null) {
                return cls;
            }
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            try {
                while (true) {
                    len = fis.read(buffer);
                    if (len == -1) {
                        break;
                    }
                    baos.write(buffer, 0, len);
                }
//FileInputStream的flush是空操作，因为flush的作用是把缓存中的东西写入实体(硬盘或网络流)中，这里没有这种必要所以为空
//baos.flush();
                byte[] data = baos.toByteArray();
                return defineClass(null, data, 0, data.length);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    baos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

}
