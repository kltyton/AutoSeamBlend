package com.kltyton.autoseamblend.frontend.port;

/**
 * 中文：由未来正式入口提供的纯退出回调；工作台本身不选择命令、按键或 {@code setScreen} 策略。
 *
 * <p>English: Pure exit callback supplied by the future formal entry. The
 * workbench itself chooses no command, key binding, or {@code setScreen}
 * policy.
 */
@FunctionalInterface
public interface WorkbenchExitPort {
    void exit(Reason reason);

    /** 中文：已经由控制器验证的退出原因。 / English: Controller-validated exit reason. */
    enum Reason {
        CLEAN,
        DISCARDED,
        SAVED
    }

}
