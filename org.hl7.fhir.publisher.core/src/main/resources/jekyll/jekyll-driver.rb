# frozen_string_literal: true
#
# Watch mode helper for the FHIR IG Publisher (see JekyllDriver.java): a Jekyll that stays running between the builds
# of a watch session, so that when only some pages changed it renders those pages, and the pages that include what
# changed, instead of the whole site again.
#
# It runs in the temp folder (the Jekyll source), and reads one command per line on stdin, as JSON:
#
#   {"cmd":"build"}                    a full build, the same as `jekyll build --destination <dest>`
#   {"cmd":"update","changed":[...]}   the files changed since the last command (paths relative to the source): the
#                                      output becomes what a full build would write. When it can't be sure of that
#                                      for the change, it does a full build. With "keep":true, an output file that
#                                      would be written with what Jekyll last wrote there is left as it is (the
#                                      caller knows what has changed it since), and "written" lists the files that
#                                      were written, also for a full build when it can tell
#   {"cmd":"rewrite","files":[...]}    writes these output files (paths relative to the destination) again as the
#                                      last build wrote them; "unknown" lists the ones Jekyll doesn't write
#
# Jekyll's log goes to stdout as it does for `jekyll build`; each command ends with one line that starts with MARK,
# followed by the outcome as JSON.

$stdout.sync = true
$stderr.reopen($stdout)
$stderr.sync = true

require "digest"
require "json"
require "set"

MARK = "@@ig-jekyll-driver@@ "

def reply(outcome)
  $stdout.write("#{MARK}#{JSON.generate(outcome)}\n")
end

DEST = ARGV[0]

begin
  require "jekyll"
  Jekyll::PluginManager.require_from_bundler
  Jekyll::External.require_if_present(Jekyll::External.blessed_gems)
rescue Exception => e # rubocop:disable Lint/RescueException
  reply("ok" => false, "unavailable" => "Jekyll could not be loaded: #{e.class}: #{e.message}")
  exit 1
end

module IgJekyllDriver
  # Liquid markup that makes a page depend on more than its own source, the includes it renders, and _data: other
  # pages (site.pages etc.), the time, or counters shared between pages. The site variable is a name of its own: not
  # part of a longer name (body-site-cs, page.site; a hyphen after {{ or before }} controls whitespace), the name of an
  # include's parameter (site=...), or part of a quoted string, which are taken out first (unless they may hold markup)
  LIQUID_MARKUP = %r{\{\{.*?\}\}|\{%.*?%\}}m.freeze
  QUOTED = %r{"[^"{}%]*"|'[^'{}%]*'}.freeze
  RISKY_MARKUP = %r{(?<![\w.])(?<!\w-)site(?!\w|-\w)(?!\s*\.\s*data(?!\w|-\w))(?!\s*=(?!=))|\A\{%-?\s*(?:increment|decrement)\b}.freeze
  QUICK_CHECK = %r{\bsite\b|increment|decrement}.freeze

  class << self
    # page source path => the files it read while it was rendered (includes)
    attr_reader :deps
    # the set that the page being rendered adds to
    attr_accessor :current
    # templates with risky markup, as they were parsed
    attr_reader :risky
    # output file => what Jekyll last wrote there: a digest of a page's output, the stat of a static file's source
    attr_reader :wrote
    # the output files written by the command that's running, or nil when they can't all be known
    attr_reader :written
    # whether the command that's running leaves an output file that would get what Jekyll last wrote there as it is
    attr_reader :keep

    def reset
      @deps = {}
      @risky = []
      @current = nil
    end

    def risky?(markup)
      RISKY_MARKUP.match?(markup.gsub(QUOTED, "''"))
    end

    def scan(name, content)
      s = content.to_s
      return unless QUICK_CHECK.match?(s)

      s.scan(LIQUID_MARKUP) do |m|
        if risky?(m)
          @risky << "#{name}: #{m[0, 80]}"
          return
        end
      end
    end

    # Before each command
    def start_command
      @written = Set.new
      @keep = false
    end

    # Whether the command may leave the output files that would get what Jekyll last wrote there as they are, from now
    # on (only while it knows which files it writes)
    def keep_unchanged(keep)
      @keep = keep && !@written.nil?
    end

    # When a build is about to write the site: what's written can only be known (and left as it is) when it's all
    # plain pages and static files, each written to a place of its own, and nothing hooks into their writing
    def site_writes(site, hooked)
      return if @written.nil?
      return unless hooked || !site.docs_to_write.empty? || !site.pages.all? { |p| p.instance_of?(Jekyll::Page) } ||
                    !site.static_files.all? { |f| f.instance_of?(Jekyll::StaticFile) } || Driver.shared_destination(site)

      @written = nil
      @keep = false
    end

    # Forgets what was written (when it may not have been written whole)
    def forget_writes
      @wrote = {}
      @written = nil
      @keep = false
    end

    # Writes an output file unless keeping it is allowed and what Jekyll last wrote there would be written again
    def write(path, stamp)
      if @keep && stamp && @wrote[path] == stamp && File.file?(path)
        return false
      end

      @wrote.delete(path)
      yield
      @wrote[path] = stamp if stamp
      @written&.add(path)
      true
    end

    # What identifies a static file's source as it is now: if it's the same, so is its content (nil if unknown)
    def source_stamp(path)
      st = File.stat(path)
      [st.size, st.mtime.to_r, st.ctime.to_r, st.ino, st.dev]
    rescue SystemCallError
      nil
    end
  end
  reset
  forget_writes

  module RendererHook
    def run
      prev = IgJekyllDriver.current
      key = document.is_a?(Jekyll::Page) ? File.join(site.source, document.relative_path) : nil
      IgJekyllDriver.current = key ? (IgJekyllDriver.deps[key] = Set.new) : nil
      super
    ensure
      IgJekyllDriver.current = prev
    end
  end

  module InclusionHook
    def render(context)
      IgJekyllDriver.current&.add(File.expand_path(path))
      super
    end
  end

  module ReadFileHook
    def read_file(file, context)
      IgJekyllDriver.current&.add(File.expand_path(file))
      super
    end
  end

  module ParseHook
    def parse(content)
      IgJekyllDriver.scan(@filename, content) unless @renderer.cache.key?(@filename)
      super
    end
  end

  module SiteWriteHook
    def write
      IgJekyllDriver.site_writes(self, Driver.hooks?)
      super
    end
  end

  module PageWriteHook
    def write(dest)
      IgJekyllDriver.write(destination(dest), Digest::SHA1.digest(output.to_s)) { super }
    end
  end

  # (always copies when it writes: Jekyll's own check of the source's time isn't what decides)
  module StaticWriteHook
    def write(dest)
      target = destination(dest)
      IgJekyllDriver.write(target, IgJekyllDriver.source_stamp(path)) do
        FileUtils.rm_f(target)
        super
      end
    end
  end
end

Jekyll::Renderer.prepend(IgJekyllDriver::RendererHook)
Jekyll::Inclusion.prepend(IgJekyllDriver::InclusionHook)
Jekyll::Tags::IncludeTag.prepend(IgJekyllDriver::ReadFileHook)
Jekyll::LiquidRenderer::File.prepend(IgJekyllDriver::ParseHook)
Jekyll::Site.prepend(IgJekyllDriver::SiteWriteHook)
Jekyll::Page.prepend(IgJekyllDriver::PageWriteHook)
Jekyll::StaticFile.prepend(IgJekyllDriver::StaticWriteHook)

# A change that only a full build can be sure to handle
class NeedFull < StandardError; end

class Driver
  def initialize(dest)
    @dest = dest
    @site = nil
  end

  def run
    while (line = $stdin.gets)
      line = line.strip
      next if line.empty?

      begin
        command = JSON.parse(line)
      rescue JSON::ParserError => e
        reply("ok" => false, "error" => "bad command: #{e.message}")
        next
      end
      break if command["cmd"] == "quit"

      IgJekyllDriver.start_command

      if File.exist?("Gemfile")
        reply("ok" => false, "unavailable" => "the site has a Gemfile")
        next
      end
      case command["cmd"]
      when "build" then reply(full_build)
      when "update" then reply(update(Array(command["changed"]), command["keep"] == true))
      when "rewrite" then reply(rewrite(Array(command["files"])))
      else reply("ok" => false, "error" => "unknown command #{command["cmd"]}")
      end
    end
  end

  private

  def log_start
    Jekyll.logger.info "Source:", @site_source || Dir.pwd
    Jekyll.logger.info "Destination:", File.expand_path(@dest)
    Jekyll.logger.info "Incremental build:", "disabled. Enable with --incremental"
    Jekyll.logger.info "Generating..."
  end

  def log_done(start)
    Jekyll.logger.info "", "done in #{(Time.now - start).round(3)} seconds."
  end

  # What `jekyll build` reports when the build fails (see Jekyll::Command)
  def failed(error)
    @site = nil
    IgJekyllDriver.forget_writes
    if error.is_a?(Jekyll::Errors::FatalException)
      Jekyll.logger.error "ERROR:", "YOUR SITE COULD NOT BE BUILT:"
      Jekyll.logger.error "", "------------------------------------"
      Jekyll.logger.error "", error.message
    else
      msg = " Please append `--trace` to the `build` command "
      dashes = "-" * msg.length
      Jekyll.logger.error "", dashes
      Jekyll.logger.error "Jekyll #{Jekyll::VERSION} ", msg
      Jekyll.logger.error "", " for any additional information or backtrace. "
      Jekyll.logger.error "", dashes
    end
    { "ok" => false, "error" => "#{error.class}: #{error.message}" }
  end

  # A build of the whole site; with keep, the output files that would get what Jekyll last wrote there are left as
  # they are, and the result lists the ones written (when that can be known)
  def full_build(reason = nil, keep: false)
    @site = nil
    IgJekyllDriver.reset
    Jekyll::StaticFile.reset_cache
    Jekyll.sites.clear
    start = Time.now
    options = { "destination" => @dest, "serving" => false }
    Jekyll.logger.adjust_verbosity(options)
    site = Jekyll::Site.new(Jekyll.configuration(options))
    @site_source = site.source
    log_start
    IgJekyllDriver.keep_unchanged(keep)
    site.process
    IgJekyllDriver.keep_unchanged(false)
    log_done(start)
    @site = site
    @full_only = full_only_reason(site)
    res = { "ok" => true, "full" => true, "reason" => reason, "fullOnly" => @full_only }
    res["written"] = IgJekyllDriver.written.map { |d| rel_dest(d) }.sort if keep && IgJekyllDriver.written
    res
  rescue Exception => e # rubocop:disable Lint/RescueException
    failed(e)
  end

  # Why changes to this site can't be rendered on their own, if they can't: anything that lets a page's output depend
  # on more than its own source, the files it includes, and _data
  def full_only_reason(site)
    return "the site has layouts" unless site.layouts.empty?
    return "the site has generators" unless site.generators.empty?
    return "the site has documents or collections" unless site.documents.empty?
    return "the site has plugins" if site.plugin_manager.plugins_path.any? { |p| Dir.exist?(p) && !Dir.empty?(p) }
    return "the site uses hooks" if hooks?
    return "page excerpts are on" if site.config["page_excerpts"]
    return "the site's configuration asks for incremental builds or profiling" if site.config["incremental"] || site.config["profile"]

    paths = site.pages.map(&:path)
    return "two pages have the same path" unless paths.uniq.size == paths.size
    return "#{Driver.shared_destination(site)} is written by two pages or files" if Driver.shared_destination(site)
    return "a page's output depends on more than its own source: #{IgJekyllDriver.risky.first}" unless IgJekyllDriver.risky.empty?

    nil
  end

  # The hooks that jekyll-sass-converter always registers only tell its converters which page they convert
  BENIGN_HOOKS = %r{/jekyll-sass-converter-[^/]+/lib/jekyll/converters/scss\.rb\z}.freeze

  def self.hooks?
    registry = Jekyll::Hooks.instance_variable_get(:@registry)
    return true unless registry.is_a?(Hash)

    registry.values.any? do |events|
      events.values.any? do |list|
        list.any? { |hook| !BENIGN_HOOKS.match?(hook.respond_to?(:source_location) ? hook.source_location&.first.to_s : "") }
      end
    end
  end

  def hooks?
    Driver.hooks?
  end

  # An output file that more than one page, static file or document is written to, if there's one
  def self.shared_destination(site)
    seen = Set.new
    items = site.pages + site.static_files.select(&:write?) + site.docs_to_write
    items.each do |item|
      dest = item.destination(site.dest)
      return dest.delete_prefix(File.join(site.dest, "")) unless seen.add?(dest)
    end
    nil
  end

  def abs_source(rel)
    File.expand_path(rel, @site.source)
  end

  def page_source(page)
    File.join(@site.source, page.relative_path)
  end

  def include_file?(abs)
    @site.includes_load_paths.any? { |dir| abs.start_with?(File.join(File.expand_path(dir), "")) }
  end

  # keep: see full_build
  def update(changed, keep)
    return full_build("there was no build to go on from") unless @site
    return full_build(@full_only, keep: keep) if @full_only

    start = Time.now
    pages = {}
    @site.pages.each_with_index { |p, i| pages[page_source(p)] = i }
    statics = {}
    @site.static_files.each { |f| statics[File.expand_path(f.path)] = f }

    paths = Set.new
    changed_pages = []
    changed_statics = []
    changed.each do |rel|
      abs = abs_source(rel)
      return full_build("#{rel} is not there (any more)") unless File.file?(abs)

      paths << abs
      if pages.key?(abs)
        return full_build("#{rel} no longer has front matter") unless Jekyll::Utils.has_yaml_header?(abs)

        changed_pages << pages[abs]
      elsif statics.key?(abs)
        return full_build("#{rel} now has front matter") if Jekyll::Utils.has_yaml_header?(abs)

        changed_statics << statics[abs]
      elsif !include_file?(abs)
        return full_build("#{rel} is not a page, a static file or an include")
      end
    end

    # the pages to render again: the changed ones, and those that read a changed file
    render = Set.new(changed_pages)
    IgJekyllDriver.deps.each do |src, read|
      render << pages[src] if pages.key?(src) && read.intersect?(paths)
    end

    @site_source = @site.source
    log_start
    forget(paths)
    IgJekyllDriver.keep_unchanged(keep)
    render_pages(render.to_a.sort)
    return full_build(@full_only, keep: keep) if (@full_only = full_only_reason(@site))

    changed_statics.each { |f| f.write(@site.dest) }
    IgJekyllDriver.keep_unchanged(false)
    Jekyll::Cleaner.new(@site).cleanup!
    log_done(start)
    { "ok" => true, "full" => false, "written" => IgJekyllDriver.written.map { |d| rel_dest(d) }.sort }
  rescue NeedFull => e
    full_build(e.message, keep: keep)
  rescue Exception => e # rubocop:disable Lint/RescueException
    failed(e)
  end

  def rewrite(files)
    return { "ok" => false, "error" => "there is no build to go on from" } unless @site

    targets = {}
    @site.pages.each_with_index { |p, i| targets[p.destination(@site.dest)] = [:page, i] }
    @site.static_files.each { |f| targets[f.destination(@site.dest)] = [:static, f] if f.write? }
    render = []
    statics = []
    unknown = []
    files.each do |rel|
      kind, what = targets[File.expand_path(rel, @site.dest)]
      if kind.nil?
        unknown << rel
      elsif kind == :page
        render << what
      else
        statics << what
      end
    end
    render_pages(render.uniq.sort)
    statics.uniq.each { |f| f.write(@site.dest) }
    { "ok" => true, "written" => IgJekyllDriver.written.map { |d| rel_dest(d) }.sort, "unknown" => unknown }
  rescue NeedFull => e
    { "ok" => false, "error" => e.message }
  rescue Exception => e # rubocop:disable Lint/RescueException
    failed(e)
  end

  # Forgets what was read from these files: the parsed templates and the include contents
  def forget(paths)
    cache = @site.liquid_renderer.cache
    paths.each do |abs|
      cache.delete(@site.liquid_renderer.send(:normalize_path, abs))
    end
    @site.inclusions.delete_if { |_, inclusion| inclusion.nil? || paths.include?(File.expand_path(inclusion.path)) }
  end

  # Renders the pages at these indexes in site.pages again, from a fresh read of their source (rendering a page
  # replaces its content), and then writes them (so nothing is written if one fails)
  def render_pages(indexes)
    return if indexes.empty?

    payload = @site.site_payload
    cache = @site.liquid_renderer.cache
    pages = indexes.map do |i|
      old = @site.pages[i]
      raise NeedFull, "#{old.relative_path} is not a plain page" unless old.instance_of?(Jekyll::Page)

      page = Jekyll::Page.new(@site, @site.source, old.instance_variable_get(:@dir), old.name)
      raise NeedFull, "#{old.relative_path} would not be published now" unless @site.publisher.publish?(page)
      raise NeedFull, "#{old.relative_path} would be written somewhere else now" unless page.destination(@site.dest) == old.destination(@site.dest)
      raise NeedFull, "#{old.relative_path} has a different path now" unless page.path == old.path

      [i, page]
    end
    pages.each do |i, page|
      cache.delete(@site.liquid_renderer.send(:normalize_path, page.path))
      @site.pages[i] = page
      @site.send(:render_regenerated, page, payload)
    end
    pages.each { |_, page| page.write(@site.dest) }
  end

  def rel_dest(path)
    path.delete_prefix(File.join(@site.dest, ""))
  end
end

Driver.new(DEST).run
