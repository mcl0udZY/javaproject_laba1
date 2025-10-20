package laba1;

public class GenerateBooks {
    public static void main(String[] args) {
        try {
            Main.Library lib = new Main.Library();

            // добавляем несколько тестовых книг
            lib.add(new Main.Book("Преступление и наказание", "Ф. М. Достоевский", 1866, "978-5-389-07478-7", "Роман"));
            lib.add(new Main.Book("Мастер и Маргарита", "М. А. Булгаков", 1967, "978-5-389-07479-4", "Роман"));
            lib.add(new Main.Book("Чистый код", "Robert C. Martin", 2008, "978-0-13-235088-4", "Программирование"));
            lib.add(new Main.Book("Алгоритмы: построение и анализ", "Т. Кормен и др.", 2009, "978-5-907144-31-3", "Учебник"));
            lib.add(new Main.Book("Три товарища", "Эрих Мария Ремарк", 1936, "978-5-17-118366-3", "Роман"));

            String path = "books.ser";
            lib.save(path);
            System.out.println("Файл успешно создан: " + path);
        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }
}
