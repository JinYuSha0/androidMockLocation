package com.sjy.dd;

public class MockLoopThread extends Thread {
    private boolean sign;
    private Action action;
    private int sleepTime;

    MockLoopThread(Action action, int sleepTime) {
        this.sign = true;
        this.action = action;
        this.sleepTime = sleepTime;
    }

    @Override
    public void run() {
        while (this.sign) {
            try {
                Thread.sleep(this.sleepTime);
                this.action.todo();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void end() {
        this.sign = false;
        this.action.end();
    }

    public interface Action<T> {
        void todo();
        void end();
    }
}
