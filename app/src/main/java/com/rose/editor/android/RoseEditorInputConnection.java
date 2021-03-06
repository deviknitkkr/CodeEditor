/*
 Copyright 2020 Rose2073

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package com.rose.editor.android;

import android.view.inputmethod.BaseInputConnection;

import com.rose.editor.common.Cursor;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextUtils;
import com.rose.editor.simpleclass.CharPosition;
import com.rose.editor.common.Content;
import android.text.SpannableStringBuilder;

/**
 * Connection between input method and editor
 * @author Rose
 */
class RoseEditorInputConnection extends BaseInputConnection {

    private RoseEditor mEditor;

    protected int composingLine = -1;
    protected int composingStart = -1;
    protected int composingEnd = -1;
    private boolean invalid;

    /**
     * Create a connection for the given editor
     * @param targetView Host editor
     */
    public RoseEditorInputConnection(RoseEditor targetView) {
        super(targetView, true);
        mEditor = targetView;
        invalid = false;
    }

    protected void invalid() {
        invalid = true;
        composingEnd = composingStart = composingLine = -1;
        mEditor.invalidate();
    }

    /**
     * Reset the state of this connection
     */
    protected void reset() {
        composingEnd = composingStart = composingLine = -1;
        invalid = false;
    }

    /**
     * Private use.
     * Get the Cursor of Content displaying by Editor
     * @return Cursor
     */
    private Cursor getCursor() {
        return mEditor.getCursor();
    }

    @Override
    public Editable getEditable() {
        // This action is not supported by editor
        // We handle all the requests ourself
        return null;
    }

    @Override
    public void closeConnection() {
        super.closeConnection();
        mEditor.onCloseConnection();
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        return TextUtils.getCapsMode(mEditor.getText(), getCursor().getLeft(), reqModes);
    }

    private CharSequence getTextRegionInternal(int start, int end, int flags) {
        Content origin = mEditor.getText();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start < 0) {
            start = 0;
        }
        if (end > origin.length() - 1) {
            end = origin.length() - 1;
        }
        if (end < start) {
            start = end = 0;
        }
        Content sub = (Content)origin.subSequence(start, end);
        if (flags == GET_TEXT_WITH_STYLES) {
            // TODO
            return new SpannableStringBuilder(sub);
        }
        return sub.toString();
    }

    private CharSequence getTextRegion(int start, int end, int flags) {
        try {
            return getTextRegionInternal(start, end, flags);
        } catch (IndexOutOfBoundsException|IllegalArgumentException e) {
            return flags == GET_TEXT_WITH_STYLES ? new SpannableStringBuilder() : "";
        }
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        return getTextRegion(getCursor().getLeft(), getCursor().getRight(), flags);
    }

    @Override
    public CharSequence getTextBeforeCursor(int length, int flags) {
        int start = getCursor().getLeft();
        return getTextRegion(start - length, start, flags);
    }

    @Override
    public CharSequence getTextAfterCursor(int length, int flags) {
        int end = getCursor().getRight();
        return getTextRegion(end, end + length, flags);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (!mEditor.isEditable() || invalid) {
            return false;
        }
        // NOTE: Text styles are ignored by editor
        //Remove composing text first if there is
        deleteComposingText();
        // newCursorPosition ignored
        // Call this can make auto indent and delete text selected automatically
        getCursor().onCommitText(text);
        return true;
    }

    private void deleteComposingText() {
        if (composingLine == -1) {
            return;
        }
        try {
            mEditor.getText().delete(composingLine, composingStart, composingLine, composingEnd);
        } catch(IndexOutOfBoundsException e){
            e.printStackTrace();
        }
        composingLine = composingStart = composingEnd = -1;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (!mEditor.isEditable() || invalid) {
            return false;
        }
        if(beforeLength == afterLength && beforeLength == 0) {
            getCursor().onDeleteKeyPressed();
            return true;
        }
        int rangeEnd = getCursor().getLeft();
        int rangeStart = rangeEnd - beforeLength;
        if (rangeStart < 0) {
            rangeStart = 0;
        }
        mEditor.getText().delete(rangeStart, rangeEnd);
        rangeStart = getCursor().getRight();
        rangeEnd = rangeStart + afterLength;
        if (rangeEnd > mEditor.getText().length()) {
            rangeEnd = mEditor.getText().length();
        }
        mEditor.getText().delete(rangeStart, rangeEnd);
        return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        // Unsupported operation
        // According to document, we should return false
        return false;
    }

    @Override
    public boolean beginBatchEdit() {
        return mEditor.getText().beginBatchEdit();
    }

    @Override
    public boolean endBatchEdit() {
        boolean inBatch = mEditor.getText().endBatchEdit();
        if (!inBatch) {
            mEditor.updateSelection();
        }
        return inBatch;
    }

    private void deleteSelected() {
        if (getCursor().isSelected()) {
            // Delete selected text
            getCursor().onDeleteKeyPressed();
        }
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (!mEditor.isEditable() || invalid) {
            return false;
        }
        if (composingLine == -1) {
            // Create composing info
            deleteSelected();
            composingLine = getCursor().getLeftLine();
            composingStart = getCursor().getLeftColumn();
            composingEnd = composingStart + text.length();
            getCursor().onCommitText(text);
        } else {
            // Already have composing text
            // Delete first
            if (composingStart != composingEnd) {
                mEditor.getText().delete(composingLine, composingStart, composingLine, composingEnd);
            }
            // Reset range
            composingEnd = composingStart + text.length();
            mEditor.getText().insert(composingLine, composingStart, text);
        }
        return true;
    }

    @Override
    public boolean finishComposingText() {
        if (!mEditor.isEditable() || invalid) {
            return false;
        }
        composingLine = composingStart = composingEnd = -1;
        return true;
    }

    @Override
    public boolean setSelection(int start, int end) {
        if (!mEditor.isEditable() || invalid) {
            return false;
        }
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start < 0) {
            start = 0;
        }
        Content content = mEditor.getText();
        if (end > content.length()) {
            end = content.length();
        }
        CharPosition startPos = content.getIndexer().getCharPosition(start);
        CharPosition endPos = content.getIndexer().getCharPosition(end);
        getCursor().setLeft(startPos.line, startPos.column);
        getCursor().setRight(endPos.line, endPos.column);
        mEditor.invalidate();
        return true;
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        if (!mEditor.isEditable() || invalid) {
            return false;
        }
        try {
            if (start > end) {
                int tmp = start;
                start = end;
                end = tmp;
            }
            if (start < 0) {
                start = 0;
            }
            Content content = mEditor.getText();
            if (end > content.length()) {
                end = content.length();
            }
            CharPosition startPos = content.getIndexer().getCharPosition(start);
            CharPosition endPos = content.getIndexer().getCharPosition(end);
            if(startPos.line != endPos.line) {
                return false;
            }
            composingLine = startPos.line;
            composingStart = startPos.column;
            composingEnd = endPos.column;
            mEditor.invalidate();
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        mEditor.updateCursorAnchor();
        return true;
    }

}
