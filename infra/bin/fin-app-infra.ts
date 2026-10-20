/**
 * The CDK app: the AWS resources of the recruiter chatbot, defined in code.
 *
 * Deploy through `mcp-server/deploy-mcp.sh`, which builds the profile index and the bundle first.
 * Running `npx cdk deploy` directly would deploy whatever is currently in `mcp-server/dist/lambda/`.
 */
import { App, Tags } from 'aws-cdk-lib'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { ProfileMcpStack } from '../lib/profile-mcp-stack.ts'

/** The bundle built by `npm run bundle` in `mcp-server/`. */
const BUNDLE_DIR = path.resolve(import.meta.dirname, '../../mcp-server/dist/lambda')

/** The region of the whole deployment (EC2, Bedrock and this stack). */
const REGION = 'eu-north-1'

for (const file of ['index.mjs', 'index.json']) {
  if (!existsSync(path.join(BUNDLE_DIR, file))) {
    throw new Error(
      `${path.join(BUNDLE_DIR, file)} is missing. Build it with mcp-server/deploy-mcp.sh ` +
      '(or npm run build:index and npm run bundle in mcp-server/).',
    )
  }
}

const app = new App()

new ProfileMcpStack(app, 'FinAppProfileMcp', {
  description: 'Recruiter chatbot: read-only profile MCP server on Lambda',
  // The account comes from the credentials the CDK CLI runs with (your AWS profile).
  env: { account: process.env['CDK_DEFAULT_ACCOUNT'], region: REGION },
  bundleDir: BUNDLE_DIR,
  backendRoleName: 'fin-app-ec2-bedrock',
})

// Every resource gets this tag, so the project's resources are easy to find (and to see on the bill).
Tags.of(app).add('project', 'fin-app')