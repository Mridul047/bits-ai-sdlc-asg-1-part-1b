/**
 * Layer 1 evaluator — structural validation.
 * Checks INV-001 (response matches output contract schema) and
 * INV-006 (insights empty when status != OK; 1-5 when OK).
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import Ajv from 'ajv';

const __dirname = dirname(fileURLToPath(import.meta.url));
const schemaDir = resolve(__dirname, '../schema');

const ajv = new Ajv({ allErrors: true });

const successSchema = JSON.parse(
  readFileSync(resolve(schemaDir, 'insights-response.schema.json'), 'utf8')
);
const errorSchema = JSON.parse(
  readFileSync(resolve(schemaDir, 'error-response.schema.json'), 'utf8')
);

const validateSuccess = ajv.compile(successSchema);
const validateError = ajv.compile(errorSchema);

/**
 * @param {number} httpStatus
 * @param {object} body - parsed response body
 * @returns {{ inv001: boolean, inv006: boolean, errors: string[] }}
 */
export function evaluateStructural(httpStatus, body) {
  const result = { inv001: false, inv006: false, errors: [] };

  if (httpStatus === 200) {
    const valid = validateSuccess(body);
    if (!valid) {
      result.errors.push(
        ...validateSuccess.errors.map(
          (e) => `INV-001 schema: ${e.instancePath} ${e.message}`
        )
      );
    } else {
      result.inv001 = true;
    }

    // INV-006: insights empty when not OK; 1-5 when OK
    const status = body?.status;
    const insights = body?.insights ?? [];
    if (status === 'OK') {
      if (insights.length >= 1 && insights.length <= 5) {
        result.inv006 = true;
      } else {
        result.errors.push(
          `INV-006: status is OK but insights.length=${insights.length} (expected 1-5)`
        );
      }
    } else {
      if (insights.length === 0) {
        result.inv006 = true;
      } else {
        result.errors.push(
          `INV-006: status=${status} but insights.length=${insights.length} (expected 0)`
        );
      }
    }
  } else {
    const valid = validateError(body);
    if (!valid) {
      result.errors.push(
        ...validateError.errors.map(
          (e) => `INV-001 error schema: ${e.instancePath} ${e.message}`
        )
      );
    } else {
      result.inv001 = true;
      // INV-006 not applicable for non-200 responses
      result.inv006 = true;
    }
  }

  return result;
}
