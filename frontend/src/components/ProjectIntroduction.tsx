export function ProjectIntroduction() {
  return (
    <section id="about" class="project-introduction" aria-labelledby="project-introduction-title">
      <div class="project-introduction-copy">
        <p class="eyebrow">Why this project exists</p>
        <h2 id="project-introduction-title">A small, inspectable LLM application.</h2>
        <p class="project-summary">
          AI Demo shows how provider-independent chat, streaming, and tool calls fit together in a
          Java application—without hiding the request flow behind an AI framework.
        </p>
        <p class="project-purpose">
          Built as a reference for developers who want to understand the moving parts, compare
          providers, and run the same application locally or in the cloud.
        </p>
        <a class="source-link" href="https://github.com/MartinBelas/ai-demo" target="_blank" rel="noopener noreferrer">
          <span>View source on GitHub</span><span aria-hidden="true">↗</span><span class="visually-hidden">Opens in a new tab</span>
        </a>
      </div>

      <aside class="project-constraints" aria-label="Current limitations">
        <p class="project-constraints-title">Current limitations</p>
        <ol>
          <li>Models can be inaccurate</li>
          <li>History stays in this browser</li>
          <li>Only the latest 10 messages are sent</li>
          <li>Available providers depend on this deployment</li>
        </ol>
      </aside>
    </section>
  );
}
