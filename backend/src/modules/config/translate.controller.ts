import { Body, Controller, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { IsOptional, IsString, MaxLength } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

class TranslateDto {
  @ApiProperty()
  @IsString()
  @MaxLength(4000)
  text: string;

  @ApiPropertyOptional({ description: 'Target language code, e.g. ar, en, tr' })
  @IsOptional()
  @IsString()
  @MaxLength(16)
  targetLang?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(16)
  sourceLang?: string;
}

@ApiTags('Translate')
@ApiBearerAuth()
@Controller('translate')
export class TranslateController {
  @Post()
  @ApiOperation({ summary: 'Translate text (Google free gtx, MyMemory fallback)' })
  async translate(@Body() dto: TranslateDto) {
    const text = (dto.text || '').trim();
    if (!text) return { translated: '', targetLang: dto.targetLang || 'en' };
    const target = (dto.targetLang || 'en').split('-')[0].toLowerCase();
    const source = (dto.sourceLang || 'auto').split('-')[0].toLowerCase();

    const google = await this.translateGoogle(text, source, target);
    if (google) {
      return {
        translated: google,
        targetLang: target,
        sourceLang: source,
        provider: 'google',
      };
    }

    try {
      const q = encodeURIComponent(text.slice(0, 500));
      const langpair =
        source === 'auto' ? `autodetect|${target}` : `${source}|${target}`;
      const url = `https://api.mymemory.translated.net/get?q=${q}&langpair=${langpair}`;
      const res = await fetch(url);
      const json: any = await res.json();
      const translated =
        json?.responseData?.translatedText ||
        json?.matches?.[0]?.translation ||
        text;
      return {
        translated: String(translated),
        targetLang: target,
        sourceLang: source,
        provider: 'mymemory',
      };
    } catch {
      return {
        translated: text,
        targetLang: target,
        sourceLang: source,
        provider: 'fallback',
      };
    }
  }

  /** Free unofficial Google Translate endpoint (same family as translate.google.com/gtx). */
  private async translateGoogle(
    text: string,
    source: string,
    target: string,
  ): Promise<string | null> {
    try {
      const sl = source === 'auto' ? 'auto' : source;
      const params = new URLSearchParams({
        client: 'gtx',
        sl,
        tl: target,
        dt: 't',
        q: text.slice(0, 4500),
      });
      const url = `https://translate.googleapis.com/translate_a/single?${params.toString()}`;
      const res = await fetch(url, {
        headers: {
          'User-Agent':
            'Mozilla/5.0 (compatible; JEHO CHAT/1.0; +https://api.adnova.bbs.tr)',
          Accept: '*/*',
        },
      });
      if (!res.ok) return null;
      const json: any = await res.json();
      if (!Array.isArray(json) || !Array.isArray(json[0])) return null;
      const parts: string[] = [];
      for (const chunk of json[0]) {
        if (Array.isArray(chunk) && typeof chunk[0] === 'string') {
          parts.push(chunk[0]);
        }
      }
      const out = parts.join('').trim();
      return out || null;
    } catch {
      return null;
    }
  }
}
