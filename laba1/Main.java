package laba1;

import java.io.*;
import java.util.*;

/**
 * консольный менеджер библиотеки.
 * функции добавить/редактировать/список/поиск, сохранить/загрузить (ser), обработка ошибок.
 * ооп инкапсуляция (private поля + геттеры/сеттеры),наследование Publication Book),полиморфизм(kind()).
 */
public class Main {

    // модель
    /**
     * Базовый/абстрактный тип публикации.
     * тут общий набор полей и логика отображения наследники добавляют своё (пример Book).
     */
    static abstract class Publication implements Serializable {
        private String title;
        private String author;
        private int year;

        public Publication(String title, String author, int year) {
            this.title = title;
            this.author = author;
            this.year = year;
        }

        /** полиморфный тип публикации для красивого вывода и потенциальных других наследников*/
        public abstract String kind();

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public int getYear() { return year; }
        public void setYear(int year) { this.year = year; }

        @Override
        public String toString() {
            // форматируем строку в читаемом виде kind() даст конкретный тип
            return "%s{title='%s', author='%s', year=%d}".formatted(kind(), title, author, year);
        }
    }

    /**
     * конкретный тип книга показываем наследование от Publication.
     * Добавляем ISBN уникальный ключ и жанр.
     */
    static class Book extends Publication {
        private String isbn;
        private String genre;

        public Book(String title, String author, int year, String isbn, String genre) {
            super(title, author, year);
            this.isbn = isbn;
            this.genre = genre;
        }

        @Override
        public String kind() { return "Book"; }

        public String getIsbn() { return isbn; }
        public void setIsbn(String isbn) { this.isbn = isbn; }

        public String getGenre() { return genre; }
        public void setGenre(String genre) { this.genre = genre; }

        @Override
        public String toString() {
            // вывод, добавляя isbn и жанр
            return super.toString().replace("}", ", isbn='%s', genre='%s'}".formatted(isbn, genre));
        }
    }

    // !!КАСТОМНЫЕ ИСКЛЮЧЕНИЯ!!
    // даём говорящие типы ошибок под логику предметной области читаемее чем generic Runtime
    static class ValidationException extends Exception {
        public ValidationException(String msg) { super(msg); }
    }
    static class NotFoundException extends Exception {
        public NotFoundException(String msg) { super(msg); }
    }
    static class DuplicateException extends Exception {
        public DuplicateException(String msg) { super(msg); }
    }

    //СЕРВИС ЛОГИКА ПРИЛОЖЕНИЯ
    /**
     * хранлище книг и операции над ним.
     * Выбор LinkedHashMap сохраняем порядок добавления
     * Ключ нормализованный ISBN без дефисов
     */
    static class Library {
        private final Map<String, Book> books = new LinkedHashMap<>();

        /** добавление книги чек корректности + уникальность по ISBN */
        public void add(Book book) throws ValidationException, DuplicateException {
            validate(book); // проверяем поля перед любыми действиями (fail-fast)
            String key = normalizeIsbn(book.getIsbn()); // единый формат ключа
            if (books.containsKey(key))
                throw new DuplicateException("Книга с таким ISBN уже есть: " + book.getIsbn());
            books.put(key, book);
        }

        /** обнова по старому ISBN поддержка смены ISBN с защитой от коллизий. */
        public void update(String isbn, Book newData)
                throws NotFoundException, ValidationException, DuplicateException {
            String key = normalizeIsbn(isbn);
            if (!books.containsKey(key))
                throw new NotFoundException("Не найден ISBN: " + isbn);

            validate(newData);
            String newKey = normalizeIsbn(newData.getIsbn());

            // если ISBN меняем на уже существующий ловим коллизию
            if (!newKey.equals(key) && books.containsKey(newKey))
                throw new DuplicateException("Новый ISBN уже существует: " + newData.getIsbn());

            // удалили старый ключ, положили по новому
            books.remove(key);
            books.put(newKey, newData);
        }

        /** список всех книг (копия чтобы не светить внутреннюю структуру)*/
        public List<Book> list() { return new ArrayList<>(books.values()); }

        /** получить книгу по ISBN (NotFound читаемая ошибка для UI */
        public Book get(String isbn) throws NotFoundException {
            Book b = books.get(normalizeIsbn(isbn));
            if (b == null) throw new NotFoundException("Не найден ISBN: " + isbn);
            return b;
        }

        /**
         * поиск по нескольким полям
         * используем Stream API читаемо и легко расширяемо.
         * логика: если фильтр пустой не учитываем если задан проверяем условие.
         */
        public List<Book> search(String title, String author, String genre, Integer year, String isbnPart) {
            return books.values().stream().filter(b -> {
                boolean ok = true;
                // containsIgnoreCase
                if (notBlank(title))   ok &= b.getTitle().toLowerCase().contains(title.toLowerCase());
                if (notBlank(author))  ok &= b.getAuthor().toLowerCase().contains(author.toLowerCase());
                if (notBlank(genre))   ok &= b.getGenre().toLowerCase().contains(genre.toLowerCase());
                if (year != null)      ok &= b.getYear() == year;
                if (notBlank(isbnPart)) ok &= b.getIsbn().contains(isbnPart);
                return ok;
            }).toList(); // возвращаем List
        }

        /**
         * сохранение через стандартную сериализацию
         * посему ок: быстрый способ утащить/притащить состояние без ручного парсинга
         * минусы: бинарный формат
         */
        public void save(String path) throws IOException {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
                out.writeObject(books.values().stream().toList());
            }
        }

        /**
         * загрузка из сериализованного файла
         * скипаем дубликаты/битые записи (add() сам валидирует) UX загружается всё что валидно
         */
        @SuppressWarnings("unchecked")
        public void load(String path) throws IOException, ClassNotFoundException {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
                List<Book> list = (List<Book>) in.readObject();
                for (Book b : list) {
                    try { add(b); } catch (Exception ignore) {} // дубликаты/валидация скипаем строку
                }
            }
        }

        //хелперы
        private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

        /** нормализуем ISBN убираем дефисы один канонический ключ. */
        private static String normalizeIsbn(String s) { return s.replace("-", ""); }

        /** валидация входных данных лучше упасть здесьчем тащить мусор в хранилище */
        private static void validate(Book b) throws ValidationException {
            if (!notBlank(b.getTitle()))  throw new ValidationException("Название не может быть пустым");
            if (!notBlank(b.getAuthor())) throw new ValidationException("Автор не может быть пустым");
            if (b.getYear() < 0 || b.getYear() > 3000) throw new ValidationException("Некорректный год");
            // Допускаем ISBN-10/13 с дефисами. Для реального проекта можно дописать контрольную сумму.
            if (!b.getIsbn().matches("[0-9\\-]{10,17}"))
                throw new ValidationException("Некорректный ISBN (ожидается 10/13 цифр, можно с дефисами)");
        }
    }

    // КОНСОЛЬНОЕ МЕНЮ
    /**
     * мейн цикл печатаем меню читаем команду выполняем действие ловим ошибки
     * общий try/catch вокруг switch чтобы любой фейл не крашил приложение
     */
    public static void main(String[] args) {
        Library library = new Library();
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("""
                    === Библиотека ===
                    1) Добавить книгу
                    2) Редактировать книгу
                    3) Список книг
                    4) Поиск книги
                    5) Сохранить в файл
                    6) Загрузить из файла
                    0) Выход
                    Выберите пункт: """);

            String cmd = sc.nextLine().trim();
            try {
                switch (cmd) {
                    case "1" -> addBook(library, sc); // ввод полей + add()
                    case "2" -> editBook(library, sc); // поиск по ISBN + update()
                    case "3" -> library.list().forEach(System.out::println);
                    case "4" -> searchBooks(library, sc); // фильтры + stream().filter()
                    case "5" -> { // сериализация в .ser
                        System.out.print("Файл: ");
                        library.save(sc.nextLine().trim());
                        System.out.println("Сохранено.");
                    }
                    case "6" -> { // чтение из .ser
                        System.out.print("Файл: ");
                        library.load(sc.nextLine().trim());
                        System.out.println("Загружено.");
                    }
                    case "0" -> { System.out.println("Выход."); return; }
                    default -> System.out.println("Неизвестная команда");
                }
            } catch (Exception e) {
                // один обработчик на все кейсы
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    /** диалог добавления читаем поля из консоли и валидируем через add() */
    private static void addBook(Library library, Scanner sc)
            throws ValidationException, DuplicateException {
        System.out.print("Название: ");
        String title = sc.nextLine();
        System.out.print("Автор: ");
        String author = sc.nextLine();
        System.out.print("Год: ");
        int year = Integer.parseInt(sc.nextLine()); // если не число улетит в общий catch
        System.out.print("ISBN: ");
        String isbn = sc.nextLine();
        System.out.print("Жанр: ");
        String genre = sc.nextLine();

        library.add(new Book(title, author, year, isbn, genre));
        System.out.println("Книга добавлена!");
    }

    /**
     * редактирование тянем текущую книгу по ISBN показываем старые значения.
     * если юзер жмёт просто Enter  оставляем прежнее (readOrDefault()).
     */
    private static void editBook(Library library, Scanner sc)
            throws NotFoundException, ValidationException, DuplicateException {
        System.out.print("Введите ISBN для редактирования: ");
        String oldIsbn = sc.nextLine();
        Book old = library.get(oldIsbn); // NotFoundException если нет поймается выше
        System.out.println("Текущие данные: " + old);

        System.out.print("Новое название (" + old.getTitle() + "): ");
        String title = readOrDefault(sc, old.getTitle());
        System.out.print("Автор (" + old.getAuthor() + "): ");
        String author = readOrDefault(sc, old.getAuthor());
        System.out.print("Год (" + old.getYear() + "): ");
        int year = Integer.parseInt(readOrDefault(sc, String.valueOf(old.getYear())));
        System.out.print("ISBN (" + old.getIsbn() + "): ");
        String isbn = readOrDefault(sc, old.getIsbn());
        System.out.print("Жанр (" + old.getGenre() + "): ");
        String genre = readOrDefault(sc, old.getGenre());

        library.update(oldIsbn, new Book(title, author, year, isbn, genre));
        System.out.println("Обновлено.");
    }

    /** поиск по нескольким атрибутам пустой ввод фильтигнорится */
    private static void searchBooks(Library library, Scanner sc) {
        System.out.print("Название содержит: ");
        String title = sc.nextLine();
        System.out.print("Автор содержит: ");
        String author = sc.nextLine();
        System.out.print("Жанр содержит: ");
        String genre = sc.nextLine();
        System.out.print("Год (=): ");
        String yearStr = sc.nextLine();
        Integer year = yearStr.isBlank() ? null : Integer.parseInt(yearStr);
        System.out.print("ISBN содержит: ");
        String isbnPart = sc.nextLine();

        List<Book> found = library.search(title, author, genre, year, isbnPart);
        if (found.isEmpty()) System.out.println("Ничего не найдено.");
        else found.forEach(System.out::println);
    }

    /**
     * хелпер для редактирования если юзер просто нажал Enter оставляем прежнее значение
     */
    private static String readOrDefault(Scanner sc, String def) {
        String s = sc.nextLine();
        return s.isBlank() ? def : s.trim();
    }
}
