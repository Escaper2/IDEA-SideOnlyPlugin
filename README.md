# IDEA-SideOnlyPlugin

Плагин реализующий инспекцию ошибок и инлэй хинты для аннотации SideOnly.

## Аннотация SideOnly
При разработке мультиплеерных игр часто возникает ситуация, когда код должен испольняться только на клиентской, либо на серверной стороне.
Поэтому была создана аннотация @SideOnly, которая вырезает аннотированный элемент из клиентских/серверных билдов: 

```java
public class CommonClass {

    public void commonMethod() {
        System.out.println("This method exists both on the client and on the server side");
    }

    @SideOnly(Side.SERVER)
    public void serverMethod() {
        System.out.println("This method exists only on the server side");
    }
}
```
Подобный инструмент решает описанную выше проблему, но при попытке использовать аннотированный `@SideOnly` элемент из общего контекста происходит ошибка на этапе сборки. Разработка была бы удобнее, если интегрировать этот инструмент с IDE:

```java
public class CommonClass {

    public void commonMethod() {
        // Было бы классно, если бы ошибка сразу подсвечивалась
        serverMethod(); // <-- ERROR
    }

    @SideOnly(Side.SERVER)
    public void serverMethod() {
        System.out.println("This method exists only on the server side");
    }
}
```
Аннотацию можно установить на: классы, интерфейсы, методы и конструкторы.

Если на элемент установлена аннотация `@SideOnly`, то после сборки он останется только на указанных сторонах. Если аннотация не установлена, то поведение аналогично ситуации, когда установлена аннотация `@SideOnly({Side.CLIENT, Side.SERVER})`.

### Родительские элементы
Кроме того, в рамках механизма вычисления сторон у элементов есть понятие **родительских элементов**:
- Для любых классов super класс является родительским элементом
- Для любых классов и интерфейсов все super интерфейсы являются родительскими элементами
- Для nested классов их outer класс является родительским элементом
- Для anonymous классов, объявленных внутри метода, этот метод является родительским элементом
- Для любых методов класс, в котором они объявлены, является родительским элементом

Для вычисления множества сторон элемента берется _пересечение_ множества сторон из аннотации этого элемента со всеми множествами сторон его родительских элементов.

### Примеры

```java
@SideOnly(Side.CLIENT)
public class ClientClass {
    // Итоговая сторона: CLIENT
    public void someMethod() {}

    // Итоговая сторона: CLIENT
    public class ClientInnerClass {}
}
```

```java
@SideOnly(Side.SERVER)
public class ServerClass {}

// Итоговая сторона: SERVER
public class ServerClassChild extends ServerClass {}

// Итоговая сторона: SERVER
public class ServerClassGrandchild extends ServerClassChild {}
```

```java
@SideOnly(Side.CLIENT)
public interface ClientInterface {}

// Итоговая сторона: CLIENT
public class SomeClass implements ClientInterface {}
```

```java
public class CommonClass {
    @SideOnly(Side.SERVER)
    public void serverMethod() {
        // Этот анонимный класс тоже существует только на стороне SERVER
        Runnable r = new Runnable() {
            @Override
            public void run() {}
        };
    }
}
```

```java
@SideOnly(Side.CLIENT)
public class ClientClass {}

// В результате пересечения сторон получится пустое множество.
// Этого класса не будет ни на одной из сторон.
// Это валидное, хоть и бессмысленное поведение.
@SideOnly(Side.SERVER)
public class NoSideClass extends ClientClass {}
```

## Фичи, которые добавляет плагин:
### Inlay Inspection
[Класс](https://github.com/Escaper2/IDEA-SideOnlyPlugin/blob/master/src/main/java/escaper2/testtask/sideonlyplugin/SideOnlyInspectionTool.java) SideOnlyInspectionTool наследуется от AbstractBaseJavaLocalInspectionTool
и реализует подсветку ошибок при использовании любых элементов в неправильном контексте, что не допустит возможности сбилдить такой проект. 

Как показывалось в примере выше, при любой попытке использовать ограниченный по сторонам элемент может произойти ошибка на этапе сборки, если место использования должно быть доступно с других сторон. Другими словами, ошибка происходит:
- при попытке использовать серверный элемент из клиентского либо общего кода
- при попытке использовать клиентский элемент из серверного либо общего кода
- при любой попытке использования элемента, не доступного ни на одной из сторон

Пример:

![image](https://github.com/Escaper2/IDEA-SideOnlyPlugin/blob/master/img/InlayInspection%20example.png)

### Inlay Hint
[Класс](https://github.com/Escaper2/IDEA-SideOnlyPlugin/blob/master/src/main/java/escaper2/testtask/sideonlyplugin/SideOnlyHintProvider.java) SideOnlyHintProvider имплементирует интерфейс InlayHintsProvider 
и реализует показ подсказок, которые помогают понять, на каких сторонах доступен определенный элемент. 


Подсказка показывается если соблюдаются следующие условия:
- Он доступен не на всех сторонах
- Непосредственно на этом элементе не стоит аннотации `@SideOnly`

Пример:

![image](https://github.com/Escaper2/IDEA-SideOnlyPlugin/blob/master/img/InlayHint%20example.png)
