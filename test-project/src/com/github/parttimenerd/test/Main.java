package com.github.parttimenerd.test;

public class Main {

    public static void main(String[] args) {
        System.out.println(fib(40));
        System.out.println(new Bla().fib(40));
        System.out.println(new Bla() {
            public int fib(int x) {
                return super.fib(x - 1);
            }
        }.fib(40));
    }

    public static int fib(int x) {
        if (x > 2) {
            int y = x;
            while (y < 10000000) {
                y++;
            }
            return fib(x - 1) + fib(x - 2);
        }
        return 1;
    }

    public static class Bla {
        public int fib(int x) {
            return Main.fib(x);
        }
    }
}
