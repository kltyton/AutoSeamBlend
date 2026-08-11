package com.kltyton.autoseamblend.authoring.workbench;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;

/**
 * 中文：工作台草稿必须公开的作者字段；具体草稿结构仍由 Loader 保持所有权。
 *
 * English:
 * Authoring fields required from a workbench draft. The loader retains
 * ownership of each concrete draft structure.
 */
public interface WorkbenchDraftFields {
    ConnectionMethod requestedMethod();

    boolean compatibility();
}
