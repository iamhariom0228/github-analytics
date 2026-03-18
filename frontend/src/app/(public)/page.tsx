import Link from "next/link";

export default function LandingPage() {
  return (
    <main className="min-h-screen bg-[#0d1117] text-white">
      {/* Nav */}
      <nav className="border-b border-white/10 px-6 py-4 flex items-center justify-between max-w-7xl mx-auto">
        <span className="font-bold text-xl">GitHub Analytics</span>
        <a
          href="/api/backend/auth/github"
          className="bg-white text-black px-4 py-2 rounded-md text-sm font-medium hover:bg-gray-100 transition"
        >
          Connect GitHub
        </a>
      </nav>

      {/* Hero */}
      <section className="max-w-4xl mx-auto px-6 py-24 text-center">
        <h1 className="text-5xl font-bold mb-6 bg-gradient-to-r from-blue-400 to-purple-400 bg-clip-text text-transparent">
          Insights GitHub doesn&apos;t show you
        </h1>
        <p className="text-xl text-gray-400 mb-10 max-w-2xl mx-auto">
          PR lifecycle timing, contribution heatmaps, team leaderboards, review turnaround metrics,
          bus-factor detection, and weekly email digests — all in one dashboard.
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <a
            href="/api/backend/auth/github"
            className="inline-block bg-blue-600 hover:bg-blue-700 text-white px-8 py-4 rounded-lg text-lg font-semibold transition"
          >
            Connect GitHub for free
          </a>
          <a
            href="/api/demo-login"
            className="inline-block bg-white/10 hover:bg-white/20 border border-white/20 text-white px-8 py-4 rounded-lg text-lg font-semibold transition"
          >
            Try Demo
          </a>
        </div>
      </section>

      {/* Feature grid */}
      <section className="max-w-5xl mx-auto px-6 pb-24 grid grid-cols-1 md:grid-cols-3 gap-6">
        {[
          {
            icon: "🔥",
            title: "Contribution Heatmap",
            desc: "See exactly when you code — hour by day of week, just like GitHub's calendar but granular.",
          },
          {
            icon: "⏱",
            title: "PR Lifecycle",
            desc: "Track average time from open → first review → merge. Find your bottlenecks.",
          },
          {
            icon: "🏆",
            title: "Team Leaderboard",
            desc: "Commits, PRs, reviews per contributor. Spot stale PRs and bus-factor risk.",
          },
          {
            icon: "📬",
            title: "Weekly Email Digest",
            desc: "Monday morning summary of last week's activity, streaks, and one personalized insight.",
          },
          {
            icon: "🚌",
            title: "Bus Factor Detection",
            desc: "Know when one person owns >50% of commits in a repo before it becomes a risk.",
          },
          {
            icon: "⚡",
            title: "Real-time via Webhooks",
            desc: "Dashboard updates within seconds of a push or PR event — no manual refresh needed.",
          },
        ].map((f) => (
          <div key={f.title} className="bg-white/5 border border-white/10 rounded-xl p-6">
            <div className="text-3xl mb-3">{f.icon}</div>
            <h3 className="font-semibold text-lg mb-2">{f.title}</h3>
            <p className="text-gray-400 text-sm">{f.desc}</p>
          </div>
        ))}
      </section>

      {/* Stack badge */}
      <div className="text-center pb-12 text-gray-600 text-sm">
        Built with Java 21 · Spring Boot 3 · Next.js 14 · Kafka · Redis · PostgreSQL
      </div>
    </main>
  );
}
