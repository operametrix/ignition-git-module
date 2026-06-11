import React from "react";

interface Props {
  path: string;
  oldText: string;
  newText: string;
}

// Simple side-by-side view of a file's old vs new content at a commit.
const DiffViewer = ({ path, oldText, newText }: Props) => {
  const oldContent = oldText ? oldText : "(empty)";
  const newContent = newText ? newText : "(empty)";
  return (
    <div className="gitcfg-diff">
      <div className="gitcfg-diff-title">{path}</div>
      <div className="gitcfg-diff-cols">
        <pre className="gitcfg-diff-old">{oldContent}</pre>
        <pre className="gitcfg-diff-new">{newContent}</pre>
      </div>
    </div>
  );
};

export default DiffViewer;
