import { Link } from 'react-router-dom'
import avatarUrl from '../assets/avatar.png'

const CV = {
  name: 'Long Nghiem',
  role: 'Software developer',
  greetingFi: 'Moi, olen Long',
  greetingEn: "Hi, I'm Long",
  location: 'Helsinki, Finland',
  phone: '044 359 7091',
  email: 'longsuomi@gmail.com',
  summary:
    "I'm a full-stack developer with 7 years of experience across frontend, backend, mobile and DevOps. " +
    "I like contributing to every stage of a project — from data modelling and implementation, to deployment " +
    "and the long tail of maintenance. My goals are simple: keep getting better, and use that craft to make " +
    "something that matters.",
  competencies: [
    { group: 'Backend',    items: ['Kotlin', 'Java', 'Spring Boot', 'jOOQ', 'PostgreSQL', 'Flyway', 'REST', 'Event-driven'] },
    { group: 'Frontend',   items: ['React', 'TypeScript', 'WebSocket', 'Semantic UI'] },
    { group: 'Mobile',     items: ['Android (Kotlin)', 'Voice-directed (EdgeVUI)', 'Zebra & Honeywell scanners'] },
    { group: 'Testing',    items: ['Selenium (Web)', 'Appium (Mobile)', 'JUnit 5', 'Mockito'] },
    { group: 'DevOps',     items: ['GitLab CI/CD', 'Docker Compose', 'Ansible', 'AWS'] },
    { group: 'Agentic AI', items: ['Claude Code', 'Claude Design', 'GitHub Copilot', 'Pi Coding Agent'] },
  ],
  experience: [
    {
      role: 'Software developer',
      company: 'Optiscan Oy',
      city: 'Helsinki',
      period: '04/2020 — 06/2026',
      current: true,
      bullets: [
        { title: 'Warehouse Management Systems', body: 'Planned, developed and maintained multiple WMS platforms and integrated mobile solutions for enterprise clients.' },
        { title: 'Core product development', body: "Built the company's core product and shared libraries in Kotlin, Spring Boot, PostgreSQL, jOOQ and React. End-to-end coverage with Selenium and Appium; multi-modal mobile apps using voice and barcode." },
        { title: 'Legacy & integration support', body: 'Fixed bugs across Java 7/8, Spring, Hibernate, PostgreSQL and JSP, and triaged incidents in Apache ServiceMix (XML integrations).' },
        { title: 'Custom mobile apps', body: 'Single-handedly built full barcode-scanning apps for client workflows.' },
        { title: 'DevOps', body: 'Designed and optimised CI/CD pipelines in GitLab, containerised services with Docker, and automated infrastructure with Ansible.' },
      ],
    },
    {
      role: 'Software developer',
      company: 'Nord Pool Oy',
      city: 'Espoo',
      period: '01/2019 — 03/2020',
      bullets: [
        { title: '24/7 electricity trading platform', body: 'Maintained and developed Java backend microservices in Java 8, Spring Boot, MsSQL and AngularJS. Prototyped a Slickgrid-based frontend rebuild.' },
      ],
    },
    {
      role: 'Junior developer',
      company: 'Integrify',
      city: 'Helsinki',
      period: '04/2018 — 12/2018',
      bullets: [
        { title: 'Web applications', body: 'Built small web apps with JavaScript, React, MongoDB, Node and Express.' },
      ],
    },
    {
      role: 'Back-end developer intern',
      company: 'Combase USA, Inc',
      city: 'Remote',
      period: '2013',
      bullets: [
        { title: 'Sales forecasting', body: 'Created a sales-forecast module using Java and MySQL.' },
      ],
    },
  ],
  education: [
    { title: 'Bachelor degree in Business Information Technology', org: 'Lahti University of Applied Sciences', period: '2009 — 2014' },
    { title: 'Erasmus exchange programme', org: 'University of Wolverhampton, England', period: '09/2011 — 01/2012' },
  ],
  hobbies: ['Board games', 'Video games', 'Badminton'],
} as const

const sectionLabelCls = 'text-[11px] font-bold text-amber tracking-[1px] uppercase m-0'
const cardCls = 'bg-surface border border-border rounded-[14px] px-6 py-[22px] shadow-card'
const pillCls =
  'inline-flex items-center bg-surface-alt text-text-primary border border-border rounded-full ' +
  'px-[11px] py-[5px] text-xs font-semibold font-mono tracking-[-0.2px]'

interface ContactLinkProps {
  label: string
  value: string
  href?: string
}

function ContactLink({ label, value, href }: ContactLinkProps) {
  const external = href?.startsWith('http')
  const content = (
    <>
      <span className="text-[10px] font-bold text-text-muted tracking-[0.6px] uppercase">{label}</span>
      <span className="text-[13.5px] font-semibold text-text-primary truncate font-mono">{value}</span>
    </>
  )
  const cls =
    'flex flex-col gap-0.5 px-3.5 py-2.5 rounded-[10px] bg-surface-alt border border-border ' +
    'no-underline min-w-0 transition-[background,border-color] duration-150 hover:border-amber'

  if (!href) {
    return <div className={cls}>{content}</div>
  }
  return (
    <a
      href={href}
      target={external ? '_blank' : undefined}
      rel={external ? 'noopener noreferrer' : undefined}
      className={cls}
    >
      {content}
    </a>
  )
}

export function PortfolioPage() {
  const totalTools = CV.competencies.reduce((s, g) => s + g.items.length, 0)

  return (
    <div className="page-enter max-w-[1080px] mx-auto px-6 pt-10 pb-18">
      {/* HERO */}
      <section className="grid grid-cols-1 gap-7 mb-10">
        <div className="grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_auto] items-end gap-8">
          <div className="min-w-0">
            <p className={sectionLabelCls}>About me · Tietoja minusta</p>
            <h1 className="text-[clamp(2rem,5vw,3rem)] font-extrabold text-text-primary mt-3.5 mb-1.5
                tracking-[-1.5px] leading-[1.05]">
              {CV.greetingFi}.
              <span className="block text-amber mt-0.5">{CV.role.toLowerCase()}.</span>
            </h1>
            <p className="text-[14.5px] text-text-muted mb-4 font-semibold">
              <span className="text-text-sub">{CV.greetingEn} · a {CV.role.toLowerCase()}</span>
            </p>
            <p className="text-base text-text-sub leading-[1.7] max-w-[640px] m-0 font-medium [text-wrap:pretty]">
              {CV.summary}
            </p>
          </div>

          {/* Avatar / stat card */}
          <div className="bg-surface border border-border rounded-[18px] px-[22px] py-5 min-w-[220px]
              flex flex-col gap-3.5 shadow-card">
            <div className="w-22 h-22 rounded-full overflow-hidden border-[3px] border-amber
                shadow-[0_4px_14px_rgba(255,189,89,0.35)] shrink-0">
              <img src={avatarUrl} alt={CV.name} className="w-full h-full object-cover block" />
            </div>
            <div>
              <p className="text-[15px] font-extrabold text-text-primary m-0">{CV.name}</p>
              <p className="text-[12.5px] text-text-muted mt-0.5 font-semibold">📍 {CV.location}</p>
            </div>
            <div className="h-px bg-border" />
            <div>
              <p className="text-[10px] font-bold text-text-muted tracking-[0.6px] uppercase m-0">Experience</p>
              <p className="text-xl font-bold font-mono text-text-primary mt-0.5">
                7<span className="text-[13px] text-text-muted font-medium ml-1">yrs</span>
              </p>
            </div>
          </div>
        </div>

        {/* Contact strip */}
        <div className="grid grid-cols-[repeat(auto-fit,minmax(170px,1fr))] gap-2.5">
          <ContactLink label="Email"    value={CV.email}    href={`mailto:${CV.email}`} />
          <ContactLink label="Phone"    value={CV.phone}    href={`tel:${CV.phone.replace(/\s+/g, '')}`} />
          <ContactLink label="Location" value={CV.location} />
          <ContactLink label="LinkedIn" value="/in/longnghiem" href="https://www.linkedin.com/in/longnghiem/" />
          <ContactLink label="GitHub"   value="@longnghiem"   href="https://github.com/longnghiem" />
        </div>
      </section>

      {/* COMPETENCIES */}
      <section className="mb-10">
        <header className="flex items-baseline justify-between mb-4 flex-wrap gap-2">
          <div>
            <p className={sectionLabelCls}>Competencies</p>
            <h2 className="text-2xl font-extrabold text-text-primary mt-1.5 tracking-[-0.5px]">What I work with</h2>
          </div>
          <p className="text-[13px] text-text-muted font-mono m-0">{totalTools} tools</p>
        </header>

        <div className="grid gap-3 grid-cols-[repeat(auto-fill,minmax(260px,1fr))]">
          {CV.competencies.map(c => (
            <div key={c.group} className={cardCls}>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-extrabold text-text-primary m-0 tracking-[-0.2px]">{c.group}</h3>
                <span className="text-[11px] text-text-muted font-mono font-semibold">
                  {String(c.items.length).padStart(2, '0')}
                </span>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {c.items.map(item => <span key={item} className={pillCls}>{item}</span>)}
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* EXPERIENCE */}
      <section className="mb-10">
        <header className="mb-5">
          <p className={sectionLabelCls}>Experience</p>
          <h2 className="text-2xl font-extrabold text-text-primary mt-1.5 tracking-[-0.5px]">Where I&apos;ve worked</h2>
        </header>

        <div className="relative pl-5">
          <div className="absolute left-1.5 top-2 bottom-2 w-0.5 bg-border rounded-[2px]" />

          <div className="flex flex-col gap-[18px]">
            {CV.experience.map((job, i) => (
              <div key={i} className="relative">
                <span
                  className={`absolute -left-5 top-6 w-3.5 h-3.5 rounded-full border-2 ${
                    job.current
                      ? 'bg-amber border-amber shadow-[0_0_0_4px_rgba(255,189,89,0.18)]'
                      : 'bg-surface border-border'
                  }`}
                />
                <div className={cardCls}>
                  <div className="flex items-start justify-between gap-4 flex-wrap mb-3.5">
                    <div className="flex-1 min-w-0">
                      <h3 className="text-[17px] font-extrabold text-text-primary m-0 tracking-[-0.2px]">{job.role}</h3>
                      <p className="text-sm text-text-sub mt-1 font-semibold">
                        <span className="text-accent">{job.company}</span>
                        <span className="text-text-muted font-medium"> · {job.city}</span>
                      </p>
                    </div>
                    <span
                      className={`text-[11.5px] font-mono font-semibold rounded-md px-[9px] py-1 whitespace-nowrap border ${
                        job.current
                          ? 'text-amber bg-[rgba(255,189,89,0.12)] border-[rgba(255,189,89,0.35)]'
                          : 'text-text-muted bg-surface-alt border-border'
                      }`}
                    >
                      {job.period}{job.current ? '  · current' : ''}
                    </span>
                  </div>
                  <ul className="list-none p-0 m-0 flex flex-col gap-2.5">
                    {job.bullets.map((b, bi) => (
                      <li key={bi} className="grid grid-cols-[6px_1fr] gap-3 items-start">
                        <span className="w-[5px] h-[5px] rounded-full bg-amber mt-[9px] shrink-0" />
                        <div>
                          <span className="text-[13.5px] font-extrabold text-text-primary">{b.title}.</span>
                          <span className="text-[13.5px] text-text-sub leading-[1.6] font-medium"> {b.body}</span>
                        </div>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* EDUCATION + HOBBIES */}
      <section className="grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-3.5 mb-10">
        <div className={cardCls}>
          <p className={sectionLabelCls}>Education</p>
          <div className="flex flex-col gap-4 mt-3.5">
            {CV.education.map((ed, i) => (
              <div key={i} className="border-l-2 border-border pl-3.5">
                <h3 className="text-[14.5px] font-extrabold text-text-primary m-0 leading-[1.4]">{ed.title}</h3>
                <p className="text-[13px] text-text-sub mt-[3px] mb-0.5 font-semibold">{ed.org}</p>
                <p className="text-xs text-text-muted m-0 font-mono font-semibold">{ed.period}</p>
              </div>
            ))}
          </div>
        </div>

        <div className={cardCls}>
          <p className={sectionLabelCls}>Off-screen</p>
          <h3 className="text-[14.5px] font-extrabold text-text-primary mt-3.5 mb-3">Hobbies</h3>
          <div className="flex flex-wrap gap-2">
            {CV.hobbies.map(h => <span key={h} className={pillCls}>{h}</span>)}
          </div>
          <div className="h-px bg-border my-[18px]" />
          <p className="text-[12.5px] text-text-muted m-0 leading-[1.6] italic">
            Referral letters and contact details are available upon request.
          </p>
        </div>
      </section>

      {/* CTA */}
      <section className="bg-surface border border-border rounded-[18px] px-7 py-8 flex items-center
          justify-between gap-5 flex-wrap shadow-card">
        <div className="min-w-0">
          <h2 className="text-[1.3rem] font-extrabold text-text-primary m-0 tracking-[-0.4px]">
            Want to see what I&apos;m building?
          </h2>
          <p className="text-sm text-text-sub mt-1.5 font-medium leading-[1.5]">
            This Finnish learning app is one of my side projects — built with Kotlin, Spring Boot and React.
          </p>
        </div>
        <Link
          to="/"
          className="rounded-lg bg-amber text-nav-btn-text px-5.5 py-3 text-sm font-extrabold no-underline
              inline-block whitespace-nowrap transition-colors duration-150 hover:bg-amber-hover"
        >
          Try the app →
        </Link>
      </section>

      <p className="text-center text-xs text-text-muted mt-10 font-mono font-medium">
        Kiitos käynnistä · thanks for stopping by
      </p>
    </div>
  )
}
