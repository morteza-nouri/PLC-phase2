
int age
int age

class Student < Person {
    private int age
}

class Book{
    private int price
    public initialize(int _price) {
        self.price = _price
    }
    public int getPrice() {
        return self.price
    }
}

class Person < Student {
    private int ssn
}

class Person {
    public int id
}
