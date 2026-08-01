import {
  Controller,
  Delete,
  Param,
  Post,
  UploadedFile,
  UploadedFiles,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor, FilesInterceptor } from '@nestjs/platform-express';
import { ApiTags, ApiBearerAuth, ApiConsumes, ApiBody, ApiOperation } from '@nestjs/swagger';
import { diskStorage } from 'multer';
import { extname } from 'path';
import { v4 as uuidv4 } from 'uuid';
import { UploadsService } from './uploads.service';
import { ConfigService } from '@nestjs/config';
import { AdminGuard } from '../../common/guards/admin.guard';

@ApiTags('Uploads')
@ApiBearerAuth()
@Controller('uploads')
export class UploadsController {
  constructor(
    private readonly uploadsService: UploadsService,
    private readonly configService: ConfigService,
  ) {}

  private storage() {
    const dest = this.configService.get<string>('app.uploadDir') || './uploads';
    return diskStorage({
      destination: dest,
      filename: (_req, file, cb) => {
        cb(null, `${uuidv4()}${extname(file.originalname).toLowerCase()}`);
      },
    });
  }

  @Post()
  @ApiOperation({ summary: 'Upload a single file' })
  @ApiConsumes('multipart/form-data')
  @ApiBody({
    schema: {
      type: 'object',
      properties: { file: { type: 'string', format: 'binary' } },
    },
  })
  @UseInterceptors(
    FileInterceptor('file', {
      storage: diskStorage({
        destination: process.env.UPLOAD_DIR || './uploads',
        filename: (_req, file, cb) => {
          cb(null, `${uuidv4()}${extname(file.originalname).toLowerCase()}`);
        },
      }),
      limits: { fileSize: 40 * 1024 * 1024 },
      fileFilter: (_req, file, cb) => {
        const allowed = /\.(jpg|jpeg|png|gif|webp|mp4|mp3|m4a|aac|wav|pdf|mov|webm|json)$/i;
        if (!allowed.test(extname(file.originalname))) {
          return cb(new Error('File type not allowed') as any, false);
        }
        cb(null, true);
      },
    }),
  )
  upload(@UploadedFile() file: Express.Multer.File) {
    return this.uploadsService.processUploaded(file);
  }

  @Post('multiple')
  @UseGuards(AdminGuard)
  @ApiOperation({ summary: 'Upload multiple files' })
  @ApiConsumes('multipart/form-data')
  @UseInterceptors(
    FilesInterceptor('files', 10, {
      storage: diskStorage({
        destination: process.env.UPLOAD_DIR || './uploads',
        filename: (_req, file, cb) => {
          cb(null, `${uuidv4()}${extname(file.originalname).toLowerCase()}`);
        },
      }),
      limits: { fileSize: 20 * 1024 * 1024 },
    }),
  )
  uploadMany(@UploadedFiles() files: Express.Multer.File[]) {
    return this.uploadsService.processMany(files);
  }

  @Delete(':filename')
  @UseGuards(AdminGuard)
  @ApiOperation({ summary: 'Delete an uploaded file by filename' })
  delete(@Param('filename') filename: string) {
    return this.uploadsService.deleteFilename(filename);
  }
}
