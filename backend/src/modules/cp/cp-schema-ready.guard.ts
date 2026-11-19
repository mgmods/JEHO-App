import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { CpService } from './cp.service';

/**
 * Keep CP routes unavailable until the additive CP schema and its data migration
 * are complete. Other JEHO APIs remain available during a database DDL lock.
 */
@Injectable()
export class CpSchemaReadyGuard implements CanActivate {
  constructor(private readonly cpService: CpService) {}

  canActivate(_context: ExecutionContext): boolean {
    this.cpService.assertSchemaReady();
    return true;
  }
}
