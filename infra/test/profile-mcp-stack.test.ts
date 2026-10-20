import { App } from 'aws-cdk-lib'
import { Match, Template } from 'aws-cdk-lib/assertions'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { beforeAll, describe, expect, it } from 'vitest'
import { FUNCTION_NAME, ProfileMcpStack, RESERVED_CONCURRENCY } from '../lib/profile-mcp-stack.ts'

const BACKEND_ROLE_NAME = 'fin-app-ec2-bedrock'

/** A stand-in for the real bundle: `Code.fromAsset` only needs an existing folder. */
function fakeBundleDir(): string {
  const dir = mkdtempSync(path.join(tmpdir(), 'bundle-'))
  writeFileSync(path.join(dir, 'index.mjs'), 'export const handler = async () => ({})\n')
  writeFileSync(
    path.join(dir, 'index.json'),
    '{"generatedAt":"2026-01-01T00:00:00.000Z","entries":[]}\n',
  )
  return dir
}

describe('ProfileMcpStack', () => {
  let template: Template

  beforeAll(() => {
    const app = new App()
    const stack = new ProfileMcpStack(app, 'TestStack', {
      env: { account: '123456789012', region: 'eu-north-1' },
      bundleDir: fakeBundleDir(),
      backendRoleName: BACKEND_ROLE_NAME,
    })
    template = Template.fromStack(stack)
  })

  it('testFunction_Settings', () => {
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: FUNCTION_NAME,
      Runtime: 'nodejs22.x',
      Architectures: ['arm64'],
      Handler: 'index.handler',
      MemorySize: 256,
      Timeout: 10,
      ReservedConcurrentExecutions: RESERVED_CONCURRENCY,
    })
  })

  it('testFunctionUrl_IamAuthBufferedNoCors', () => {
    template.resourceCountIs('AWS::Lambda::Url', 1)
    template.hasResourceProperties('AWS::Lambda::Url', {
      AuthType: 'AWS_IAM',
      InvokeMode: 'BUFFERED',
      Cors: Match.absent(),
    })
  })

  it('testNoResourcePolicy_NothingPublic', () => {
    // A Lambda::Permission is a resource policy on the function. With auth type NONE it would be
    // the "anyone may call" policy. This stack grants access only through the caller's role.
    template.resourceCountIs('AWS::Lambda::Permission', 0)
  })

  it('testExecutionRole_LogsOnly', () => {
    template.resourceCountIs('AWS::IAM::Role', 1)
    template.hasResourceProperties('AWS::IAM::Role', {
      AssumeRolePolicyDocument: {
        Statement: [
          Match.objectLike({
            Action: 'sts:AssumeRole',
            Principal: { Service: 'lambda.amazonaws.com' },
          }),
        ],
      },
      ManagedPolicyArns: [
        {
          'Fn::Join': [
            '',
            [
              'arn:',
              { Ref: 'AWS::Partition' },
              ':iam::aws:policy/service-role/AWSLambdaBasicExecutionRole',
            ],
          ],
        },
      ],
    })
    // The only inline policy in the stack belongs to the backend's role, not to the execution role.
    const policies = Object.values(template.findResources('AWS::IAM::Policy'))
    expect(policies.map((policy) => policy['Properties']['Roles'])).toEqual([[BACKEND_ROLE_NAME]])
  })

  it('testBackendRole_InvokeViaUrlOnly', () => {
    template.hasResourceProperties('AWS::IAM::Policy', {
      Roles: [BACKEND_ROLE_NAME],
      PolicyDocument: {
        Statement: [
          {
            Action: 'lambda:InvokeFunctionUrl',
            Effect: 'Allow',
            Condition: { StringEquals: { 'lambda:FunctionUrlAuthType': 'AWS_IAM' } },
            Resource: Match.anyValue(),
          },
          {
            Action: 'lambda:InvokeFunction',
            Effect: 'Allow',
            Condition: { Bool: { 'lambda:InvokedViaFunctionUrl': true } },
            Resource: Match.anyValue(),
          },
        ],
      },
    })
  })

  it('testLogGroup_TwoWeeks', () => {
    template.hasResourceProperties('AWS::Logs::LogGroup', {
      LogGroupName: `/aws/lambda/${FUNCTION_NAME}`,
      RetentionInDays: 14,
    })
  })

  it('testOutputs_UrlAndName', () => {
    template.hasOutput('FunctionUrl', Match.anyValue())
    template.hasOutput('FunctionName', Match.anyValue())
  })
})