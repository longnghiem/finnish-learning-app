import { CfnOutput, Duration, RemovalPolicy, Stack, type StackProps } from 'aws-cdk-lib'
import { Role } from 'aws-cdk-lib/aws-iam'
import {
  Architecture,
  Code,
  Function,
  FunctionUrlAuthType,
  InvokeMode,
  Runtime,
} from 'aws-cdk-lib/aws-lambda'
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs'
import type { Construct } from 'constructs'

/** Name of the Lambda function. The backend's logs and the runbook refer to it by this name. */
export const FUNCTION_NAME = 'fin-app-profile-mcp'

/**
 * Most copies of the function that may run at the same time; a further call gets 429. The backend
 * answers at most 4 chats at a time, each making one MCP call at a time, plus one copy of margin
 * for calls that overlap a cold start.
 */
export const RESERVED_CONCURRENCY = 5

/** Properties of {@link ProfileMcpStack}. */
export interface ProfileMcpStackProps extends StackProps {
  /**
   * Folder holding the deployment package: `index.mjs` (the bundled handler) and `index.json`
   * (the profile index). Built by `npm run bundle` in `mcp-server/`.
   */
  readonly bundleDir: string
  /**
   * Name of the existing IAM role the backend's EC2 instance wears. It is only referenced, not
   * created: the stack adds a policy to it that allows calling the function URL.
   */
  readonly backendRoleName: string
}

/**
 * The read-only profile MCP server on AWS Lambda:
 * - a function running the bundle from {@link ProfileMcpStackProps.bundleDir}, with an execution
 *   role that may only write logs, and at most {@link RESERVED_CONCURRENCY} copies at a time;
 * - its log group, kept for two weeks;
 * - a function URL that accepts only SigV4-signed requests (`AWS_IAM`), with no CORS;
 * - permission for the backend's role ({@link ProfileMcpStackProps.backendRoleName}) to call
 *   that URL, and nothing else.
 */
export class ProfileMcpStack extends Stack {
  constructor(scope: Construct, id: string, props: ProfileMcpStackProps) {
    super(scope, id, props)

    const logGroup = new LogGroup(this, 'Logs', {
      logGroupName: `/aws/lambda/${FUNCTION_NAME}`,
      retention: RetentionDays.TWO_WEEKS,
      // The logs hold no conversation content and are only for debugging.
      removalPolicy: RemovalPolicy.DESTROY,
    })

    // Without an explicit `role`, CDK creates the execution role with only the AWS managed
    // policy AWSLambdaBasicExecutionRole: the function may write logs and nothing else.
    const fn = new Function(this, 'Function', {
      functionName: FUNCTION_NAME,
      description: 'Read-only MCP server for the recruiter chatbot profile',
      runtime: Runtime.NODEJS_22_X,
      architecture: Architecture.ARM_64,
      handler: 'index.handler',
      code: Code.fromAsset(props.bundleDir),
      memorySize: 256,
      timeout: Duration.seconds(10),
      reservedConcurrentExecutions: RESERVED_CONCURRENCY,
      logGroup,
    })

    // AWS_IAM: AWS rejects every request without a valid SigV4 signature before the code runs.
    // No `cors` option, so browsers may not read the responses either.
    const url = fn.addFunctionUrl({
      authType: FunctionUrlAuthType.AWS_IAM,
      invokeMode: InvokeMode.BUFFERED,
    })

    // Adds lambda:InvokeFunctionUrl and lambda:InvokeFunction for this function to the backend's
    // role. Both are required for a function URL call.
    url.grantInvokeUrl(Role.fromRoleName(this, 'BackendRole', props.backendRoleName))

    new CfnOutput(this, 'FunctionUrl', {
      value: url.url,
      description: 'Base URL of the MCP server; the backend calls <this URL>mcp',
    })
    new CfnOutput(this, 'FunctionName', { value: fn.functionName })
  }
}